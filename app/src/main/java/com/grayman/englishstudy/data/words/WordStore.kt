package com.grayman.englishstudy.data.words

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.grayman.englishstudy.domain.StudySource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One word card. The id is the Word Pocket key of the card's original text, so the same card
 * added on two devices (or imported from Word Pocket) is the same row. It never changes after
 * creation, even when the text is edited. `updatedAt` and `deleted` exist for device sync.
 */
@Entity(tableName = "word", indices = [Index("word"), Index("nextReviewAt")])
data class WordEntity(
    @PrimaryKey val id: String,
    val word: String,
    val partOfSpeech: String,
    val meaning: String,
    val pronunciation: String,
    val example1: String,
    val example1Meaning: String,
    val example2: String,
    val example2Meaning: String,
    val memo: String,
    /** The sentence the word was found in, for words saved while reading. */
    val sourceSentence: String = "",
    val sourceUrl: String = "",
    val favorite: Boolean = false,
    /** 0-100. 100 means remembered; the card then follows the review schedule. */
    val mastery: Int = 0,
    val masteredAt: Long = 0,
    val reviewStage: Int = 0,
    /** Epoch seconds of the day the card is due again; 0 when it is not scheduled. */
    val nextReviewAt: Long = 0,
    val wrongCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    /** True while this device has a change Firestore has not confirmed yet. Local only, never synced. */
    @ColumnInfo(defaultValue = "0") val pendingSync: Boolean = false,
    /** Saved while reading (a story or a web page): studied before other new words. Synced. */
    @ColumnInfo(defaultValue = "0") val priority: Boolean = false,
    /** Added on this device and not uploaded yet, so sync sends the whole card. Local only. */
    @ColumnInfo(defaultValue = "0") val pendingCreate: Boolean = false,
) {
    val fields: WordFields
        get() = WordFields(word, partOfSpeech, meaning, pronunciation, example1, example1Meaning, example2, example2Meaning, memo)

    companion object {
        fun create(fields: WordFields, now: Long, sourceSentence: String = "", sourceUrl: String = "") = WordEntity(
            id = WordFormat.key(fields),
            word = fields.word,
            partOfSpeech = fields.partOfSpeech,
            meaning = fields.meaning,
            pronunciation = fields.pronunciation,
            example1 = fields.example1,
            example1Meaning = fields.example1Meaning,
            example2 = fields.example2,
            example2Meaning = fields.example2Meaning,
            memo = fields.memo,
            sourceSentence = sourceSentence,
            sourceUrl = sourceUrl,
            createdAt = now,
            updatedAt = now,
        )
    }
}

enum class WordFilter(val labelKo: String) {
    ALL("전체"),
    FAVORITE("즐겨찾기"),
    DUE("복습할 단어"),
    LEARNING("외우는 중"),
    MASTERED("외운 단어"),
}

data class WordCounts(val total: Int, val favorite: Int, val due: Int, val mastered: Int)

@Dao
interface WordDao {
    @Query(
        """
        SELECT * FROM word
        WHERE deleted = 0
          AND (:query = '' OR word LIKE :query || '%' OR meaning LIKE '%' || :query || '%')
          AND (:favoriteOnly = 0 OR favorite = 1)
          AND (:dueBefore = 0 OR (reviewStage > 0 AND nextReviewAt <= :dueBefore))
          AND (:masteredOnly = 0 OR mastery >= 100)
          AND (:learningOnly = 0 OR mastery < 100)
        ORDER BY word COLLATE NOCASE, partOfSpeech
        """,
    )
    fun search(
        query: String,
        favoriteOnly: Boolean,
        dueBefore: Long,
        masteredOnly: Boolean,
        learningOnly: Boolean,
    ): Flow<List<WordEntity>>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(favorite), 0) AS favorite,
               COALESCE(SUM(CASE WHEN reviewStage > 0 AND nextReviewAt <= :now THEN 1 ELSE 0 END), 0) AS due,
               COALESCE(SUM(CASE WHEN mastery >= 100 THEN 1 ELSE 0 END), 0) AS mastered
        FROM word WHERE deleted = 0
        """,
    )
    fun counts(now: Long): Flow<WordCounts>

    @Query("SELECT * FROM word WHERE id = :id")
    fun observe(id: String): Flow<WordEntity?>

    @Query("SELECT * FROM word WHERE id = :id")
    suspend fun get(id: String): WordEntity?

    @Query("SELECT * FROM word WHERE deleted = 0 AND word = :word COLLATE NOCASE ORDER BY favorite DESC, updatedAt DESC")
    suspend fun findByWord(word: String): List<WordEntity>

    /** Includes deleted rows, so a word list is seeded only once even if every card is later deleted. */
    @Query("SELECT COUNT(*) FROM word")
    suspend fun rowCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(words: List<WordEntity>): List<Long>

    @Upsert
    suspend fun upsert(word: WordEntity)

    /** Words saved while reading come first, newest first; the rest are random. */
    @Query("SELECT * FROM word WHERE deleted = 0 AND mastery < 100 ORDER BY priority DESC, CASE WHEN priority = 1 THEN -createdAt ELSE RANDOM() END LIMIT :limit")
    suspend fun randomNew(limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM word WHERE deleted = 0 AND mastery < 100 AND priority = 1")
    fun priorityCount(): Flow<Int>

    /** Words added from one source (a story id or a web address), to highlight them while reading. */
    @Query("SELECT * FROM word WHERE deleted = 0 AND sourceUrl = :sourceUrl")
    fun bySource(sourceUrl: String): Flow<List<WordEntity>>


    @Query("SELECT * FROM word WHERE deleted = 0 AND reviewStage > 0 AND nextReviewAt <= :dueBefore ORDER BY nextReviewAt LIMIT :limit")
    suspend fun due(dueBefore: Long, limit: Int): List<WordEntity>

    @Query("SELECT * FROM word WHERE deleted = 0 AND wrongCount > 0 ORDER BY wrongCount DESC, RANDOM() LIMIT :limit")
    suspend fun oftenWrong(limit: Int): List<WordEntity>

    @Query("SELECT * FROM word WHERE deleted = 0 AND favorite = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomFavorite(limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM word WHERE deleted = 0 AND wrongCount > 0")
    fun wrongCount(): Flow<Int>

    @Query("SELECT * FROM word WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<WordEntity>

    @Transaction
    suspend fun upsertAll(words: List<WordEntity>) = words.forEach { upsert(it) }

    /** Progress changes on cards Firestore already has. */
    @Query("SELECT * FROM word WHERE pendingSync = 1 AND pendingCreate = 0 LIMIT :limit")
    suspend fun pending(limit: Int): List<WordEntity>

    /** Cards added on this device that Firestore does not have yet. */
    @Query("SELECT * FROM word WHERE pendingCreate = 1 LIMIT :limit")
    suspend fun pendingCreates(limit: Int): List<WordEntity>

    @Query("UPDATE word SET pendingCreate = 0 WHERE id = :id")
    suspend fun markCreated(id: String)

    @Query("SELECT COUNT(*) FROM word WHERE pendingSync = 1")
    fun pendingCount(): Flow<Int>

    /** Clears the flag only if the row was not edited again while it was being uploaded. */
    @Query("UPDATE word SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    /** Stores cards that arrived from Firestore (see WordSyncRules.merge). Returns how many changed. */
    @Transaction
    suspend fun applyRemote(rows: List<WordEntity>): Int {
        var changed = 0
        rows.forEach { remote ->
            WordSyncRules.merge(get(remote.id), remote)?.let {
                upsert(it)
                changed++
            }
        }
        return changed
    }
}

/**
 * The app reads words and changes only study progress (favorite, remembered, review schedule).
 * Adding, editing and deleting words happens on the PC, straight in Firestore; WordSync brings
 * those changes here. The Firestore rules enforce the same split.
 */
class WordRepository(private val dao: WordDao) {

    fun search(query: String, filter: WordFilter): Flow<List<WordEntity>> = dao.search(
        query = query.trim(),
        favoriteOnly = filter == WordFilter.FAVORITE,
        dueBefore = if (filter == WordFilter.DUE) endOfTodaySeconds() else 0,
        masteredOnly = filter == WordFilter.MASTERED,
        learningOnly = filter == WordFilter.LEARNING,
    )

    fun counts(): Flow<WordCounts> = dao.counts(endOfTodaySeconds())

    fun observe(id: String): Flow<WordEntity?> = dao.observe(id)

    /** Cards saved under this word, for the look-up sheet opened from a browser. */
    suspend fun find(word: String): List<WordEntity> = dao.findByWord(word.trim())

    suspend fun setFavorite(id: String, favorite: Boolean) = update(id) { it.copy(favorite = favorite) }

    /** Marks the card remembered and schedules its first review for tomorrow. */
    suspend fun setMastered(id: String, mastered: Boolean) = update(id) {
        if (mastered) {
            it.copy(
                mastery = 100,
                masteredAt = System.currentTimeMillis() / 1000,
                reviewStage = 1,
                nextReviewAt = dayStartSeconds(LocalDate.now().plusDays(WordReview.intervalDays(1).toLong())),
            )
        } else {
            it.copy(mastery = 0, masteredAt = 0, reviewStage = 0, nextReviewAt = 0)
        }
    }

    fun wrongCount(): Flow<Int> = dao.wrongCount()

    fun priorityCount(): Flow<Int> = dao.priorityCount()

    fun bySource(sourceUrl: String): Flow<List<WordEntity>> = dao.bySource(sourceUrl)

    sealed interface AddResult {
        data class Added(val word: WordEntity) : AddResult
        /** The word book already has this word; it is now marked to study first instead. */
        data class AlreadyThere(val existing: List<WordEntity>) : AddResult
    }

    /**
     * Adds a card from this device (the app may add, but never edit or delete; the PC does that).
     * A word that is already in the book is not added twice ([WordDuplicates]); it is moved to
     * the front of the new-word queue instead, which is what saving it while reading is for.
     */
    suspend fun add(fields: WordFields, sourceSentence: String, sourceUrl: String): AddResult {
        val clean = fields.copy(word = fields.word.trim(), meaning = fields.meaning.trim())
        val existing = dao.findByWord(clean.word)
        val same = existing.filter { WordDuplicates.sameCard(it.word, it.partOfSpeech, clean.word, clean.partOfSpeech) }
        if (same.isNotEmpty()) {
            same.filter { it.mastery < 100 && !it.priority }.forEach { word -> update(word.id) { it.copy(priority = true) } }
            return AddResult.AlreadyThere(same)
        }
        val now = System.currentTimeMillis()
        val card = WordEntity.create(clean, now, sourceSentence.trim(), sourceUrl)
            .copy(priority = true, pendingSync = true, pendingCreate = true)
        dao.upsert(card)
        return AddResult.Added(card)
    }


    /** Picks the words for a test, in random order. */
    suspend fun studyWords(source: StudySource, count: Int): List<WordEntity> = when (source) {
        StudySource.NEW -> dao.randomNew(count)
        StudySource.DUE -> dao.due(endOfTodaySeconds(), count)
        StudySource.WRONG -> dao.oftenWrong(count)
        StudySource.FAVORITE -> dao.randomFavorite(count)
    }.shuffled()

    /** Saves a finished test (see WordStudyRules); sync then uploads the new progress. */
    suspend fun saveStudy(source: StudySource, ids: List<String>, failedIds: Set<String>) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        dao.upsertAll(dao.getAll(ids).map { WordStudyRules.after(it, source, it.id in failedIds, today, zone, now) })
    }

    private suspend fun update(id: String, change: (WordEntity) -> WordEntity) {
        val current = dao.get(id) ?: return
        dao.upsert(change(current).copy(updatedAt = System.currentTimeMillis(), pendingSync = true))
    }

    private fun dayStartSeconds(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

    private fun endOfTodaySeconds() = dayStartSeconds(LocalDate.now().plusDays(1)) - 1
}
