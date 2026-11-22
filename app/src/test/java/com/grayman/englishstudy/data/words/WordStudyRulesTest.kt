package com.grayman.englishstudy.data.words

import com.grayman.englishstudy.domain.StudySource
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordStudyRulesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 19)
    private val now = today.atTime(15, 0).toInstant(zone).toEpochMilli()
    private fun day(offset: Long) = today.plusDays(offset).atStartOfDay(zone).toEpochSecond()

    private val fresh = WordEntity.create(WordFields("abandon", "verb", "버리다", "", "", "", "", "", ""), 1L)

    @Test fun newWordBecomesRememberedForTomorrow() {
        val after = WordStudyRules.after(fresh, StudySource.NEW, failed = true, today, zone, now)
        assertEquals(100, after.mastery)
        assertEquals(1, after.reviewStage)
        assertEquals(day(1), after.nextReviewAt)
        assertEquals(1, after.wrongCount)
        assertTrue(after.pendingSync)
        assertEquals(now, after.updatedAt)
    }

    @Test fun reviewMovesUpOrBackToTomorrow() {
        val remembered = fresh.copy(mastery = 100, reviewStage = 2, nextReviewAt = day(0))
        val passed = WordStudyRules.after(remembered, StudySource.DUE, failed = false, today, zone, now)
        assertEquals(3, passed.reviewStage)
        assertEquals(day(7), passed.nextReviewAt)
        val missed = WordStudyRules.after(remembered, StudySource.DUE, failed = true, today, zone, now)
        assertEquals(1, missed.reviewStage)
        assertEquals(day(1), missed.nextReviewAt)
    }

    @Test fun oftenWrongTestOnlyChangesMistakes() {
        val word = fresh.copy(wrongCount = 3)
        val clean = WordStudyRules.after(word, StudySource.WRONG, failed = false, today, zone, now)
        assertEquals(2, clean.wrongCount)
        assertEquals(0, clean.mastery)
        assertEquals(0L, clean.nextReviewAt)
        assertEquals(0, WordStudyRules.after(fresh, StudySource.WRONG, failed = false, today, zone, now).wrongCount)
    }
}
