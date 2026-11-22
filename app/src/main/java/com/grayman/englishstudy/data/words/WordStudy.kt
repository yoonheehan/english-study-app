package com.grayman.englishstudy.data.words

import com.grayman.englishstudy.domain.StudySource
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a finished word test does to each card, as in Word Pocket:
 * - a word not yet remembered becomes remembered, first review tomorrow;
 * - a remembered word answered right the first time moves to the next, longer interval,
 *   and one missed even once goes back to tomorrow;
 * - every missed word counts one more mistake, and in the "often wrong" test a word
 *   answered right the first time counts one fewer.
 * Only study progress changes, which is all the app may change (see WordSyncRules).
 */
object WordStudyRules {
    fun after(
        word: WordEntity,
        source: StudySource,
        failed: Boolean,
        today: LocalDate,
        zone: ZoneId,
        nowMillis: Long,
    ): WordEntity {
        val wrongCount = when {
            failed -> word.wrongCount + 1
            source == StudySource.WRONG -> maxOf(0, word.wrongCount - 1)
            else -> word.wrongCount
        }
        fun reviewAt(stage: Int) = today.plusDays(WordReview.intervalDays(stage).toLong()).atStartOfDay(zone).toEpochSecond()
        val remembered = word.mastery >= 100
        val progressed = when {
            // The "often wrong" test only works on mistakes; it does not change what is remembered.
            source == StudySource.WRONG -> word
            !remembered -> word.copy(mastery = 100, masteredAt = nowMillis / 1000, reviewStage = 1, nextReviewAt = reviewAt(1))
            else -> {
                val stage = if (failed) WordReview.STAGE_AFTER_FAILURE else WordReview.stageAfterSuccess(word.reviewStage)
                word.copy(reviewStage = stage, nextReviewAt = reviewAt(stage))
            }
        }
        return progressed.copy(wrongCount = wrongCount, updatedAt = nowMillis, pendingSync = true)
    }
}
