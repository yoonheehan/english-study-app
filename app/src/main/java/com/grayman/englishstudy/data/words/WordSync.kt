package com.grayman.englishstudy.data.words

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.grayman.englishstudy.data.ContentSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Where the word list stands with Firestore, for the sync dialog. */
sealed interface SyncStatus {
    data object Starting : SyncStatus

    /** Signed in; waiting for the first answer from the server. */
    data class Connecting(val uid: String) : SyncStatus

    data class Connected(val uid: String) : SyncStatus

    /** The Firestore rules do not list this device's uid yet. */
    data class NotAllowed(val uid: String) : SyncStatus

    data class Failed(val uid: String?, val message: String) : SyncStatus
}

/** Pure decisions and conversions, kept apart from Firebase so they can be unit tested. */
object WordSyncRules {
    /** The only fields a device may change; the Firestore rules allow exactly these (plus syncedAt). */
    val PROGRESS_FIELDS = listOf("favorite", "mastery", "masteredAt", "reviewStage", "nextReviewAt", "wrongCount", "priority", "updatedAt")

    /**
     * What to store when a card arrives from Firestore, or null when nothing changes.
     * The card's text always comes from Firestore, because only the PC edits it. Study progress
     * follows the newer edit, so a local change not yet uploaded is not lost.
     */
    fun merge(local: WordEntity?, remote: WordEntity): WordEntity? {
        if (local == null) return remote.copy(pendingSync = false)
        val keepLocalProgress = local.updatedAt > remote.updatedAt
        val merged = if (keepLocalProgress) {
            remote.copy(
                favorite = local.favorite,
                mastery = local.mastery,
                masteredAt = local.masteredAt,
                reviewStage = local.reviewStage,
                nextReviewAt = local.nextReviewAt,
                wrongCount = local.wrongCount,
                priority = local.priority,
                updatedAt = local.updatedAt,
                pendingSync = local.pendingSync,
            )
        } else {
            remote.copy(pendingSync = false)
        }
        return merged.takeIf { it != local }
    }

    fun progressMap(word: WordEntity): Map<String, Any> = toMap(word).filterKeys { it in PROGRESS_FIELDS }

    fun toMap(word: WordEntity): Map<String, Any> = mapOf(
        "word" to word.word,
        "partOfSpeech" to word.partOfSpeech,
        "meaning" to word.meaning,
        "pronunciation" to word.pronunciation,
        "example1" to word.example1,
        "example1Meaning" to word.example1Meaning,
        "example2" to word.example2,
        "example2Meaning" to word.example2Meaning,
        "memo" to word.memo,
        "sourceSentence" to word.sourceSentence,
        "sourceUrl" to word.sourceUrl,
        "favorite" to word.favorite,
        "mastery" to word.mastery,
        "masteredAt" to word.masteredAt,
        "reviewStage" to word.reviewStage,
        "nextReviewAt" to word.nextReviewAt,
        "wrongCount" to word.wrongCount,
        "priority" to word.priority,
        "createdAt" to word.createdAt,
        "updatedAt" to word.updatedAt,
        "deleted" to word.deleted,
    )

    /** Null when the document is missing its text or timestamp, so a broken document is skipped. */
    fun fromMap(id: String, data: Map<String, Any?>): WordEntity? {
        fun text(key: String) = data[key] as? String ?: ""
        fun long(key: String) = (data[key] as? Number)?.toLong() ?: 0L
        fun int(key: String) = (data[key] as? Number)?.toInt() ?: 0
        fun bool(key: String) = data[key] as? Boolean ?: false
        val word = data["word"] as? String ?: return null
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: return null
        return WordEntity(
            id = id,
            word = word,
            partOfSpeech = text("partOfSpeech"),
            meaning = text("meaning"),
            pronunciation = text("pronunciation"),
            example1 = text("example1"),
            example1Meaning = text("example1Meaning"),
            example2 = text("example2"),
            example2Meaning = text("example2Meaning"),
            memo = text("memo"),
            sourceSentence = text("sourceSentence"),
            sourceUrl = text("sourceUrl"),
            favorite = bool("favorite"),
            mastery = int("mastery"),
            masteredAt = long("masteredAt"),
            reviewStage = int("reviewStage"),
            nextReviewAt = long("nextReviewAt"),
            wrongCount = int("wrongCount"),
            priority = bool("priority"),
            createdAt = long("createdAt"),
            updatedAt = updatedAt,
            deleted = bool("deleted"),
        )
    }
}

/**
 * Keeps the word list the same on the phone and the tablet through Firestore.
 *
 * Each device signs in anonymously; the Firestore rules allow only the two devices' uids, so
 * there is no login screen. Firestore holds the whole word list, which only the PC adds to,
 * edits or deletes from; every device downloads it into Room, the local copy the screens read.
 * The one thing a device uploads is study progress (favorite, remembered, review schedule),
 * flagged `pendingSync` until Firestore confirms it. A listener pulls changes by the server time
 * they were written, so each start reads only what changed since last time.
 */
class WordSync(
    context: Context,
    private val dao: WordDao,
    private val scope: CoroutineScope,
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("word_sync", Context.MODE_PRIVATE)
    private val vault = firestore.collection("vaults").document(VAULT_ID)
    private val words = vault.collection("words")
    private val contentSync = ContentSync(context, firestore)

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Starting)
    val status: StateFlow<SyncStatus> = _status

    private val _contentInfo = MutableStateFlow<String?>(null)
    val contentInfo: StateFlow<String?> = _contentInfo

    init {
        scope.launch { _status.collect { Log.i(TAG, "status: $it") } }
    }

    val pendingCount: Flow<Int> = dao.pendingCount()

    private var job: Job? = null

    /** Starts syncing, or restarts it (for example after this device's uid was added to the rules). */
    fun start() {
        job?.cancel()
        job = scope.launch { run() }
    }

    private suspend fun run() {
        _status.value = SyncStatus.Starting
        var uid: String? = null
        try {
            uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
                ?: error("익명 로그인 결과에 UID가 없어요")
            _status.value = SyncStatus.Connecting(uid)
            val meta = vault.get().await()
            val contentUploaded = meta.getBoolean(CONTENT_UPLOADED) == true
            coroutineScope {
                launch { pushLoop() }
                launch { pullLoop(uid) }
                launch { refreshContent(contentUploaded) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = when {
                uid != null && e is FirebaseFirestoreException &&
                    e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> SyncStatus.NotAllowed(uid)
                else -> SyncStatus.Failed(uid, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Content problems are reported but never stop word sync. */
    private suspend fun refreshContent(uploaded: Boolean) {
        try {
            val summary = contentSync.refresh(uploaded) {
                vault.set(mapOf(CONTENT_UPLOADED to true), SetOptions.merge()).await()
            }
            _contentInfo.value = "서버에서 받음: $summary (다음에 앱을 켤 때 적용)"
            Log.i(TAG, "content: $summary")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _contentInfo.value = "콘텐츠를 받지 못했어요: ${e.message ?: e.javaClass.simpleName}"
            Log.w(TAG, "content refresh failed", e)
        }
    }

    private suspend fun pushLoop() {
        pendingCount.distinctUntilChanged().collectLatest { count ->
            if (count == 0) return@collectLatest
            delay(PUSH_DEBOUNCE_MS)
            pushPending()
        }
    }

    private suspend fun pushPending() {
        pushCreates()
        while (true) {
            val batch = dao.pending(BATCH_SIZE)
            if (batch.isEmpty()) return
            val write = firestore.batch()
            batch.forEach { word ->
                // Merge only study progress into the existing card; its text belongs to the PC.
                write.set(
                    words.document(word.id),
                    WordSyncRules.progressMap(word) + (SYNCED_AT to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                )
            }
            write.commit().await()
            batch.forEach { dao.markSynced(it.id, it.updatedAt) }
            Log.i(TAG, "uploaded ${batch.size} words")
        }
    }

    /**
     * Uploads cards added on this device, one by one, as new documents. If the card already
     * exists (the PC added the same text), it is simply marked done and its progress merges later.
     * A refusal for a card that does not exist means the Firestore rules still forbid adding.
     */
    private suspend fun pushCreates() {
        while (true) {
            val batch = dao.pendingCreates(BATCH_SIZE)
            if (batch.isEmpty()) {
                _addBlocked.value = false
                return
            }
            for (word in batch) {
                val doc = words.document(word.id)
                try {
                    doc.set(WordSyncRules.toMap(word) + (SYNCED_AT to FieldValue.serverTimestamp())).await()
                    dao.markCreated(word.id)
                    dao.markSynced(word.id, word.updatedAt)
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    if (doc.get().await().exists()) {
                        dao.markCreated(word.id)
                    } else {
                        Log.w(TAG, "adding words is not allowed by the Firestore rules yet")
                        _addBlocked.value = true
                        return
                    }
                }
            }
            Log.i(TAG, "added ${batch.size} words")
        }
    }

    private val _addBlocked = MutableStateFlow(false)

    /** True when cards added here cannot upload because the Firestore rules were not updated. */
    val addBlocked: StateFlow<Boolean> = _addBlocked

    private suspend fun pullLoop(uid: String) {
        // syncedAt is the commit time, and a snapshot holds every commit up to its read time, so
        // nothing older than the newest card already seen can still be missing. The saved value
        // is cut to milliseconds, so at most the last few cards are read again.
        val since = prefs.getLong(PREF_PULLED_MS, 0L)
        val query = words
            .whereGreaterThan(SYNCED_AT, Timestamp(since / 1000, ((since % 1000) * 1_000_000).toInt()))
            .orderBy(SYNCED_AT)

        snapshots(query).collect { snapshot ->
            val rows = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                .mapNotNull { change -> WordSyncRules.fromMap(change.document.id, change.document.data)?.let { it to change } }
            if (rows.isNotEmpty()) {
                val changed = dao.applyRemote(rows.map { it.first })
                Log.i(TAG, "received ${rows.size} words, applied $changed")
                val newest = rows.maxOf { (_, change) -> change.document.getTimestamp(SYNCED_AT)?.toDate()?.time ?: 0L }
                if (newest > prefs.getLong(PREF_PULLED_MS, 0L)) prefs.edit().putLong(PREF_PULLED_MS, newest).apply()
            }
            if (!snapshot.metadata.isFromCache) _status.value = SyncStatus.Connected(uid)
        }
    }

    private fun snapshots(query: Query): Flow<QuerySnapshot> = callbackFlow {
        // Metadata changes are included so the switch from cached to server-confirmed results is
        // reported even when no card changed; that is what tells the dialog the device is connected.
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            when {
                error != null -> close(error)
                snapshot != null -> trySend(snapshot)
            }
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "WordSync"

        /** One shared word list for this person's devices. */
        const val VAULT_ID = "main"
        const val SYNCED_AT = "syncedAt"
        const val CONTENT_UPLOADED = "contentUploaded"
        const val PREF_PULLED_MS = "pulled_ms"
        const val PUSH_DEBOUNCE_MS = 1_500L

        /** Firestore allows 500 writes per batch. */
        const val BATCH_SIZE = 400
    }
}
