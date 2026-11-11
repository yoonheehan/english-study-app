package com.grayman.speakdaily.data

import android.content.Context
import androidx.room.AutoMigration
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
import com.grayman.speakdaily.domain.Sm2Scheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val levelCode: String,
    val totalXp: Int = 0,
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

@Dao
interface AppDao {
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
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "speakdaily.db").build()
    }
}

class ProgressRepository(private val dao: AppDao) {
    val profile = dao.profile()
    val progress = dao.progress()
    val dailyRecords = dao.dailyRecords()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun dueCount(): Flow<Int> = dao.dueCount(today())

    suspend fun setLevel(level: Level) {
        val current = dao.profileNow()
        dao.saveProfile(current?.copy(levelCode = level.code) ?: ProfileEntity(levelCode = level.code))
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
    }

    suspend fun completeLesson(lesson: Lesson, xp: Int, speakingSeconds: Int, score: Int) {
        dao.recordLesson(lesson.id, xp, speakingSeconds, score, lesson.phrases, today())
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
    }

    suspend fun addBonus(xp: Int, speakingSeconds: Int) {
        dao.recordLesson(null, xp, speakingSeconds, 0, emptyList(), today())
    }
}

private const val RECENT_TESTS = 3
