package com.grayman.englishstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    @Test fun exactMeaningMatches() {
        assertTrue(AnswerMatcher.matchesMeaning("포기하다", "포기하다, 버리다"))
        assertTrue(AnswerMatcher.matchesMeaning("버리다", "포기하다, 버리다"))
        assertTrue(AnswerMatcher.matchesMeaning("포기 하다", "포기하다"))
    }

    @Test fun suffixDifferencesMatch() {
        assertTrue(AnswerMatcher.matchesMeaning("행복하다", "행복한"))
        assertTrue(AnswerMatcher.matchesMeaning("중요한", "중요하다"))
        assertTrue(AnswerMatcher.matchesMeaning("경제적", "경제의"))
    }

    @Test fun parenthesesAreOptional() {
        assertTrue(AnswerMatcher.matchesMeaning("제거하다", "(문제를) 제거하다"))
        assertTrue(AnswerMatcher.matchesMeaning("문제를 제거하다", "(문제를) 제거하다"))
    }

    @Test fun oneTypoInLongMeaningIsForgiven() {
        assertTrue(AnswerMatcher.matchesMeaning("책임지는사람", "책임지은사람"))
    }

    @Test fun partialMeaningDoesNotPass() {
        assertFalse(AnswerMatcher.matchesMeaning("가능", "불가능한 일을 해내다"))
        assertFalse(AnswerMatcher.matchesMeaning("일", "불가능한 일을 해내다"))
        assertFalse(AnswerMatcher.matchesMeaning("사람", "책임지는 사람"))
        assertFalse(AnswerMatcher.matchesMeaning("", "사과"))
    }

    @Test fun oneMatchingMeaningIsEnough() {
        assertTrue(AnswerMatcher.matchesMeaning("버리다, 던지다", "포기하다, 버리다"))
    }

    @Test fun tildeAndItsParticleMayBeLeftOut() {
        assertTrue(AnswerMatcher.matchesMeaning("보다 많다", "~보다 많다"))
        assertTrue(AnswerMatcher.matchesMeaning("많다", "~보다 많다"))
        assertTrue(AnswerMatcher.matchesMeaning("이용하다", "~을 이용하다, 활용하다"))
        assertTrue(AnswerMatcher.matchesMeaning("～을 이용하다", "~을 이용하다"))
        assertTrue(AnswerMatcher.matchesMeaning("에 익숙하다", "~에 익숙하다"))
        assertFalse(AnswerMatcher.matchesMeaning("적다", "~보다 많다"))
    }

    @Test fun englishMustBeSpelledExactly() {
        assertTrue(AnswerMatcher.matchesEnglish("Abandon", "abandon"))
        assertTrue(AnswerMatcher.matchesEnglish("give up", "give up"))
        assertTrue(AnswerMatcher.matchesEnglish("colour", "color/colour"))
        assertFalse(AnswerMatcher.matchesEnglish("abandun", "abandon"))
        assertFalse(AnswerMatcher.matchesEnglish("", "abandon"))
    }
}

class WordQuizTest {
    private fun WordQuiz.askAll(correct: (QuizQuestion) -> Boolean): List<QuizQuestion> {
        val asked = mutableListOf<QuizQuestion>()
        while (!finished) {
            val q = current!!
            asked += q
            answer(correct(q))
            next()
        }
        return asked
    }

    @Test fun bothDirectionsAskMeaningsThenEnglish() {
        val quiz = WordQuiz(listOf("a", "b"), StudyDirection.BOTH)
        val asked = quiz.askAll { true }
        assertEquals(listOf(false, false, true, true), asked.map { it.askEnglish })
        assertTrue(quiz.failedIds.isEmpty())
    }

    @Test fun singleDirectionHasOneRound() {
        assertEquals(listOf(true), WordQuiz(listOf("a"), StudyDirection.ENGLISH).askAll { true }.map { it.askEnglish })
        assertEquals(listOf(false), WordQuiz(listOf("a"), StudyDirection.MEANING).askAll { true }.map { it.askEnglish })
    }

    @Test fun wrongAnswerComesBackAtTheEnd() {
        val quiz = WordQuiz(listOf("a", "b"), StudyDirection.MEANING)
        var missedOnce = false
        val asked = quiz.askAll { q -> !(q.wordId == "a" && !missedOnce).also { if (q.wordId == "a") missedOnce = true } }
        assertEquals(listOf("a", "b", "a"), asked.map { it.wordId })
        assertEquals(setOf("a"), quiz.failedIds)
        assertEquals(1, quiz.wrongAnswers)
    }

    @Test fun overrideUndoesTheWrongAnswer() {
        val quiz = WordQuiz(listOf("a", "b"), StudyDirection.MEANING)
        quiz.answer(false)
        quiz.overrideLastWrong()
        quiz.next()
        quiz.answer(true)
        quiz.next()
        assertTrue(quiz.finished)
        assertTrue(quiz.failedIds.isEmpty())
        assertEquals(0, quiz.wrongAnswers)
    }

    @Test fun remainingCountsTheCurrentQuestion() {
        val quiz = WordQuiz(listOf("a", "b", "c"), StudyDirection.MEANING)
        assertEquals(3, quiz.remaining)
        quiz.answer(false)
        assertEquals(4, quiz.remaining)
        quiz.next()
        assertEquals(3, quiz.remaining)
    }
}
