package com.grayman.englishstudy.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.grayman.englishstudy.data.words.WordDao
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.domain.Sm2Scheduler
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    /** Speaking (lesson) level. */
    val levelCode: String,
    val totalXp: Int = 0,
    /** Listening test level, set apart from speaking; null until first chosen (then speaking's is used). */
    @ColumnInfo(defaultValue = "NULL") val listeningLevelCode: String? = null,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val completions: Int,
    val bestScore: Int,
    val lastEpochDay: Long,
)

@Entity(tableName = "daily_record")
data class DailyRecordEntity(
    @PrimaryKey val epochDay: Long,
    val xp: Int,
    val speakingSeconds: Int,
    val lessons: Int,
)

@Entity(tableName = "review_card")
data class ReviewCardEntity(
    @PrimaryKey val en: String,
    val ko: String,
    val intervalDays: Int,
    val ease: Double,
    val dueEpochDay: Long,
)

/** One finished listening test. Item ids let the next test avoid replaying the same audio. */
@Entity(tableName = "listening_result")
data class ListeningResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val levelCode: String,
    val correct: Int,
    val total: Int,
    val itemIds: String,
)

/**
 * One finished activity (lesson, listening test, role-play, word test), for the history in the
 * records tab. The id is random, so the same entry from two devices is recognized when merging.
 */
@Entity(tableName = "activity_log")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val atMillis: Long,
    val epochDay: Long,
    /** One of [ActivityKind]'s names. */
    val kind: String,
    val title: String,
    val detail: String,
    val xp: Int,
)

enum class ActivityKind(val labelKo: String) { LESSON("레슨"), BONUS("한 판 더"), LISTENING("듣기"), ROLEPLAY("말하기"), WORDS("단어"), SPEAKING("스피킹") }

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activity_log")
    suspend fun allActivity(): List<ActivityEntity>

    @Query("SELECT * FROM activity_log ORDER BY atMillis DESC LIMIT :limit")
    fun recentActivity(limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_log WHERE epochDay = :day ORDER BY atMillis")
    fun activityOn(day: Long): Flow<List<ActivityEntity>>

    @Insert
    suspend fun insertListeningResult(result: ListeningResultEntity)

    @Query("SELECT * FROM listening_result ORDER BY id DESC LIMIT :limit")
    fun recentListeningResults(limit: Int): Flow<List<ListeningResultEntity>>

    @Query("SELECT itemIds FROM listening_result WHERE levelCode = :levelCode ORDER BY id DESC LIMIT :limit")
    suspend fun recentItemIds(levelCode: String, limit: Int): List<String>

    @Transaction
    suspend fun recordListeningTest(result: ListeningResultEntity, xp: Int, missedPhrases: List<Phrase>) {
        insertListeningResult(result)
        recordLesson(null, xp, 0, 0, missedPhrases, result.epochDay)
    }

    @Query("SELECT * FROM profile WHERE id = 0")
    fun profile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun profileNow(): ProfileEntity?

    @Upsert
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM lesson_progress")
    fun progress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :id")
    suspend fun progressNow(id: String): LessonProgressEntity?

    @Upsert
    suspend fun saveProgress(progress: LessonProgressEntity)

    @Query("SELECT * FROM daily_record ORDER BY epochDay DESC")
    fun dailyRecords(): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_record WHERE epochDay = :day")
    suspend fun dailyNow(day: Long): DailyRecordEntity?

    @Upsert
    suspend fun saveDaily(record: DailyRecordEntity)

    @Query("SELECT * FROM review_card WHERE dueEpochDay <= :today ORDER BY dueEpochDay LIMIT :limit")
    suspend fun dueCards(today: Long, limit: Int): List<ReviewCardEntity>

    @Query("SELECT COUNT(*) FROM review_card WHERE dueEpochDay <= :today")
    fun dueCount(today: Long): Flow<Int>

    @Query("SELECT * FROM review_card WHERE en = :en")
    suspend fun card(en: String): ReviewCardEntity?

    @Upsert
    suspend fun saveCard(card: ReviewCardEntity)

    /** Ignored on conflict, so finishing a lesson again never resets a card's schedule. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCardIfNew(card: ReviewCardEntity)

    @Query("SELECT * FROM lesson_progress")
    suspend fun allProgress(): List<LessonProgressEntity>

    @Query("SELECT * FROM daily_record")
    suspend fun allDaily(): List<DailyRecordEntity>

    @Query("SELECT * FROM review_card")
    suspend fun allCards(): List<ReviewCardEntity>

    @Query("SELECT * FROM listening_result")
    suspend fun allListening(): List<ListeningResultEntity>

    /**
     * Merges the server's records into this device's inside one transaction, so a lesson finished
     * while a sync is running is never overwritten by older numbers. Returns what was stored.
     */
    @Transaction
    suspend fun mergeIn(remote: ProgressSnapshot): ProgressSnapshot {
        val local = ProgressSnapshot(profileNow(), allProgress(), allDaily(), allCards(), allListening(), allActivity())
        val merged = ProgressMerge.merge(local, remote)
        applySnapshot(merged)
        return merged
    }

    /** Stores the merged records from a full sync (see ProgressMerge). */
    @Transaction
    suspend fun applySnapshot(snapshot: ProgressSnapshot) {
        snapshot.profile?.let { saveProfile(it) }
        snapshot.lessons.forEach { saveProgress(it) }
        snapshot.days.forEach { saveDaily(it) }
        snapshot.cards.forEach { saveCard(it) }
        val have = allListening().map { ProgressMerge.listeningKey(it) }.toSet()
        snapshot.listening.filter { ProgressMerge.listeningKey(it) !in have }.forEach { insertListeningResult(it.copy(id = 0)) }
        snapshot.activities.forEach { insertActivity(it) }
    }

    @Transaction
    suspend fun recordLesson(
        lessonId: String?,
        xp: Int,
        speakingSeconds: Int,
        score: Int,
        newPhrases: List<Phrase>,
        today: Long,
    ) {
        val profile = profileNow() ?: return
        saveProfile(profile.copy(totalXp = profile.totalXp + xp))

        val daily = dailyNow(today) ?: DailyRecordEntity(today, 0, 0, 0)
        saveDaily(
            daily.copy(
                xp = daily.xp + xp,
                speakingSeconds = daily.speakingSeconds + speakingSeconds,
                lessons = daily.lessons + if (lessonId != null) 1 else 0,
            ),
        )

        if (lessonId != null) {
            val previous = progressNow(lessonId)
            saveProgress(
                LessonProgressEntity(
                    lessonId = lessonId,
                    completions = (previous?.completions ?: 0) + 1,
                    bestScore = maxOf(previous?.bestScore ?: 0, score),
                    lastEpochDay = today,
                ),
            )
        }

        newPhrases.forEach { phrase ->
            // A new phrase comes back tomorrow, not within the same lesson.
            addCardIfNew(ReviewCardEntity(phrase.en, phrase.ko, 0, Sm2Scheduler.START_EASE, today + 1))
        }
    }
}

@Database(
    entities = [
        ProfileEntity::class,
        LessonProgressEntity::class,
        DailyRecordEntity::class,
        ReviewCardEntity::class,
        ListeningResultEntity::class,
        WordEntity::class,
        ActivityEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    abstract fun words(): WordDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "englishstudy.db").build()
    }
}

/** [onChanged] runs after every change to the study records, so they can be uploaded. */
class ProgressRepository(private val dao: AppDao, private val onChanged: () -> Unit = {}) {
    val profile = dao.profile()
    val progress = dao.progress()
    val dailyRecords = dao.dailyRecords()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun recentActivity(limit: Int = 30): Flow<List<ActivityEntity>> = dao.recentActivity(limit)

    fun activityOn(day: Long): Flow<List<ActivityEntity>> = dao.activityOn(day)

    private suspend fun log(kind: ActivityKind, title: String, detail: String, xp: Int) {
        dao.insertActivity(
            ActivityEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), today(), kind.name, title, detail, xp),
        )
    }

    fun dueCount(): Flow<Int> = dao.dueCount(today())

    /** Speaking (lesson) level. */
    suspend fun setLevel(level: Level) {
        val current = dao.profileNow()
        dao.saveProfile(current?.copy(levelCode = level.code) ?: ProfileEntity(levelCode = level.code))
        onChanged()
    }

    /** Listening test level, kept apart from the speaking level. */
    suspend fun setListeningLevel(level: Level) {
        val current = dao.profileNow() ?: ProfileEntity(levelCode = level.code)
        dao.saveProfile(current.copy(listeningLevelCode = level.code))
        onChanged()
    }

    suspend fun dueCards(limit: Int): List<ReviewCardEntity> = dao.dueCards(today(), limit)

    suspend fun gradeCard(en: String, matchPercent: Int) {
        val card = dao.card(en) ?: return
        val next = Sm2Scheduler.review(
            Sm2Scheduler.State(card.intervalDays, card.ease, card.dueEpochDay),
            Sm2Scheduler.gradeFor(matchPercent),
            today(),
        )
        dao.saveCard(card.copy(intervalDays = next.intervalDays, ease = next.ease, dueEpochDay = next.dueEpochDay))
        onChanged()
    }

    suspend fun completeLesson(lesson: Lesson, xp: Int, speakingSeconds: Int, score: Int) {
        dao.recordLesson(lesson.id, xp, speakingSeconds, score, lesson.phrases, today())
        log(ActivityKind.LESSON, lesson.titleKo, "점수 $score · 말한 시간 ${speakingSeconds}초", xp)
        onChanged()
    }

    fun recentListeningResults(limit: Int) = dao.recentListeningResults(limit)

    /** Ids from the last few tests at this level, so the next test prefers unseen items. */
    suspend fun recentlySeenListening(level: Level): Set<String> =
        dao.recentItemIds(level.code, RECENT_TESTS).flatMap { it.split(",") }.filter { it.isNotEmpty() }.toSet()

    suspend fun saveListeningTest(
        level: Level,
        correct: Int,
        total: Int,
        itemIds: List<String>,
        xp: Int,
        missedPhrases: List<Phrase>,
    ) {
        dao.recordListeningTest(
            ListeningResultEntity(
                epochDay = today(),
                levelCode = level.code,
                correct = correct,
                total = total,
                itemIds = itemIds.joinToString(","),
            ),
            xp,
            missedPhrases,
        )
        log(ActivityKind.LISTENING, "듣기 테스트 · ${level.code.uppercase()}", "${total}문제 중 ${correct}개 정답", xp)
        onChanged()
    }

    /** Everything on this device, for a full sync. */
    suspend fun snapshot(): ProgressSnapshot = ProgressSnapshot(
        profile = dao.profileNow(),
        lessons = dao.allProgress(),
        days = dao.allDaily(),
        cards = dao.allCards(),
        listening = dao.allListening(),
    )

    suspend fun mergeIn(remote: ProgressSnapshot): ProgressSnapshot = dao.mergeIn(remote)

    /** XP and speaking time outside a lesson: "한 판 더", role-play, word tests. Each one is logged. */
    suspend fun addActivity(kind: ActivityKind, title: String, detail: String, xp: Int, speakingSeconds: Int = 0) {
        dao.recordLesson(null, xp, speakingSeconds, 0, emptyList(), today())
        log(kind, title, detail, xp)
        onChanged()
    }
}

private const val RECENT_TESTS = 3
