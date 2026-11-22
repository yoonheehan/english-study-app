package com.grayman.englishstudy.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** All study records on one device: level and XP, lesson progress, daily records, review cards, listening tests. */
data class ProgressSnapshot(
    val profile: ProfileEntity?,
    val lessons: List<LessonProgressEntity>,
    val days: List<DailyRecordEntity>,
    val cards: List<ReviewCardEntity>,
    val listening: List<ListeningResultEntity>,
    val activities: List<ActivityEntity> = emptyList(),
)

/**
 * How two devices' records combine. Every rule keeps the larger or newer value, so merging the
 * same records again changes nothing, and the phone and the tablet end up with the same result
 * whichever syncs first.
 *
 * The cost of that: a lesson finished on both devices, or two devices used on the same day, count
 * once (the larger number) rather than adding up.
 */
object ProgressMerge {
    fun merge(local: ProgressSnapshot, remote: ProgressSnapshot): ProgressSnapshot {
        val days = byKey(local.days, remote.days, { it.epochDay }) { a, b ->
            DailyRecordEntity(a.epochDay, maxOf(a.xp, b.xp), maxOf(a.speakingSeconds, b.speakingSeconds), maxOf(a.lessons, b.lessons))
        }
        val lessons = byKey(local.lessons, remote.lessons, { it.lessonId }) { a, b ->
            LessonProgressEntity(a.lessonId, maxOf(a.completions, b.completions), maxOf(a.bestScore, b.bestScore), maxOf(a.lastEpochDay, b.lastEpochDay))
        }
        // The card reviewed further along (later due day, then longer interval) wins.
        val cards = byKey(local.cards, remote.cards, { it.en }) { a, b ->
            maxOf(a, b, compareBy<ReviewCardEntity>({ it.dueEpochDay }, { it.intervalDays }))
        }
        val listening = (local.listening + remote.listening).distinctBy(::listeningKey)
        val activities = (local.activities + remote.activities).distinctBy { it.id }

        // XP is the sum of the daily records, which now include both devices' days.
        val dayXp = days.sumOf { it.xp }
        val profile = when {
            local.profile == null && remote.profile == null -> null
            else -> {
                // This device keeps the level it was set to; a new device takes the other's.
                val base = local.profile ?: remote.profile!!
                base.copy(
                    totalXp = maxOf(local.profile?.totalXp ?: 0, remote.profile?.totalXp ?: 0, dayXp),
                    listeningLevelCode = base.listeningLevelCode ?: remote.profile?.listeningLevelCode,
                )
            }
        }
        return ProgressSnapshot(profile, lessons, days, cards, listening, activities)
    }

    /** Listening tests have no shared id, so the same test is recognized by its contents. */
    fun listeningKey(r: ListeningResultEntity) = "${r.epochDay}|${r.levelCode}|${r.correct}/${r.total}|${r.itemIds}"

    private fun <T, K> byKey(a: List<T>, b: List<T>, key: (T) -> K, pick: (T, T) -> T): List<T> {
        val merged = LinkedHashMap<K, T>()
        (a + b).forEach { item ->
            val k = key(item)
            merged[k] = merged[k]?.let { pick(it, item) } ?: item
        }
        return merged.values.toList()
    }

    fun toMaps(s: ProgressSnapshot): Map<String, List<Map<String, Any>>> = mapOf(
        "profile" to listOfNotNull(s.profile?.let { p -> mapOf("levelCode" to p.levelCode, "totalXp" to p.totalXp) + (p.listeningLevelCode?.let { mapOf("listeningLevelCode" to it) } ?: emptyMap()) }),
        "lessons" to s.lessons.map { mapOf("lessonId" to it.lessonId, "completions" to it.completions, "bestScore" to it.bestScore, "lastEpochDay" to it.lastEpochDay) },
        "days" to s.days.map { mapOf("epochDay" to it.epochDay, "xp" to it.xp, "speakingSeconds" to it.speakingSeconds, "lessons" to it.lessons) },
        "cards" to s.cards.map { mapOf("en" to it.en, "ko" to it.ko, "intervalDays" to it.intervalDays, "ease" to it.ease, "dueEpochDay" to it.dueEpochDay) },
        "listening" to s.listening.map {
            mapOf("epochDay" to it.epochDay, "levelCode" to it.levelCode, "correct" to it.correct, "total" to it.total, "itemIds" to it.itemIds)
        },
        "activity" to s.activities.map {
            mapOf("id" to it.id, "atMillis" to it.atMillis, "epochDay" to it.epochDay, "kind" to it.kind, "title" to it.title, "detail" to it.detail, "xp" to it.xp)
        },
    )

    /** Rows that are missing a field are skipped, so one broken entry cannot stop a sync. */
    fun fromMaps(docs: Map<String, List<Map<String, Any?>>>): ProgressSnapshot {
        fun Map<String, Any?>.str(k: String) = this[k] as? String
        fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong()
        fun Map<String, Any?>.int(k: String) = (this[k] as? Number)?.toInt()
        fun Map<String, Any?>.dbl(k: String) = (this[k] as? Number)?.toDouble()
        fun rows(name: String) = docs[name].orEmpty()
        return ProgressSnapshot(
            profile = rows("profile").firstNotNullOfOrNull { m ->
                val level = m.str("levelCode") ?: return@firstNotNullOfOrNull null
                ProfileEntity(levelCode = level, totalXp = m.int("totalXp") ?: 0, listeningLevelCode = m.str("listeningLevelCode"))
            },
            lessons = rows("lessons").mapNotNull { m ->
                LessonProgressEntity(m.str("lessonId") ?: return@mapNotNull null, m.int("completions") ?: 0, m.int("bestScore") ?: 0, m.long("lastEpochDay") ?: 0)
            },
            days = rows("days").mapNotNull { m ->
                DailyRecordEntity(m.long("epochDay") ?: return@mapNotNull null, m.int("xp") ?: 0, m.int("speakingSeconds") ?: 0, m.int("lessons") ?: 0)
            },
            cards = rows("cards").mapNotNull { m ->
                ReviewCardEntity(
                    en = m.str("en") ?: return@mapNotNull null,
                    ko = m.str("ko").orEmpty(),
                    intervalDays = m.int("intervalDays") ?: 0,
                    ease = m.dbl("ease") ?: return@mapNotNull null,
                    dueEpochDay = m.long("dueEpochDay") ?: return@mapNotNull null,
                )
            },
            listening = rows("listening").mapNotNull { m ->
                ListeningResultEntity(
                    epochDay = m.long("epochDay") ?: return@mapNotNull null,
                    levelCode = m.str("levelCode") ?: return@mapNotNull null,
                    correct = m.int("correct") ?: 0,
                    total = m.int("total") ?: 0,
                    itemIds = m.str("itemIds").orEmpty(),
                )
            },
            activities = rows("activity").mapNotNull { m ->
                ActivityEntity(
                    id = m.str("id") ?: return@mapNotNull null,
                    atMillis = m.long("atMillis") ?: return@mapNotNull null,
                    epochDay = m.long("epochDay") ?: 0,
                    kind = m.str("kind").orEmpty(),
                    title = m.str("title").orEmpty(),
                    detail = m.str("detail").orEmpty(),
                    xp = m.int("xp") ?: 0,
                )
            },
        )
    }
}

/**
 * Keeps the study records in Firestore (vaults/main/progress/{profile|lessons|days|cards|listening})
 * so they survive reinstalling and match on every device. It runs on its own: at start-up, and a
 * few seconds after any lesson, listening test, role-play or review. The "전체 동기화" button runs
 * the same thing right away. Each kind is its own document, to stay well under 1 MB per document.
 */
class ProgressSync(context: Context, private val progress: () -> ProgressRepository, private val scope: CoroutineScope) {
    data class Summary(val lessons: Int, val days: Int, val cards: Int, val listening: Int, val totalXp: Int)

    sealed interface Status {
        data object Idle : Status
        data object Syncing : Status
        data class Done(val atMillis: Long, val summary: Summary?) : Status
        data class Failed(val message: String, val permissionDenied: Boolean) : Status
    }

    private val prefs = context.getSharedPreferences("progress_sync", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val collection = firestore.collection("vaults").document("main").collection("progress")
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _status = MutableStateFlow<Status>(
        prefs.getLong(LAST, 0L).takeIf { it > 0 }?.let { Status.Done(it, null) } ?: Status.Idle,
    )
    val status: StateFlow<Status> = _status

    /** Called after every change; waits a moment so a burst of changes uploads once. */
    fun requestSync() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { syncAll() }
        }
    }

    suspend fun syncAll(): Summary = mutex.withLock {
        _status.value = Status.Syncing
        try {
            // WordSync signs in at app start; sign in here too in case that has not finished yet.
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val remoteDocs = KINDS.associateWith { kind ->
                @Suppress("UNCHECKED_CAST")
                (collection.document(kind).get().await().get(ITEMS) as? List<Map<String, Any?>>).orEmpty()
            }
            val merged = progress().mergeIn(ProgressMerge.fromMaps(remoteDocs))

            val batch = firestore.batch()
            ProgressMerge.toMaps(merged).forEach { (kind, items) ->
                batch.set(collection.document(kind), mapOf(ITEMS to items, "syncedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            }
            batch.commit().await()
            val summary = Summary(merged.lessons.count { it.completions > 0 }, merged.days.size, merged.cards.size, merged.listening.size, merged.profile?.totalXp ?: 0)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(LAST, now).apply()
            _status.value = Status.Done(now, summary)
            summary
        } catch (e: CancellationException) {
            _status.value = Status.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "progress sync failed", e)
            val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            _status.value = Status.Failed(e.message ?: e.javaClass.simpleName, denied)
            throw e
        }
    }

    private companion object {
        const val TAG = "ProgressSync"
        val KINDS = listOf("profile", "lessons", "days", "cards", "listening", "activity")
        const val ITEMS = "items"
        const val LAST = "last"
        const val DEBOUNCE_MS = 3_000L
    }
}
