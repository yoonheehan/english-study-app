package com.grayman.speakdaily.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseScorerTest {
    private val samples = listOf("It's been ages! I've been pretty busy with work.", "Good to see you again.")
    private val keywords = listOf("been", "good", "see you", "ages")

    private fun score(text: String, seconds: Double? = 4.0, confidence: Float? = 0.9f) =
        ResponseScorer.score(ResponseScorer.Input(text, keywords, samples, seconds, confidence))

    @Test
    fun `empty transcript scores zero and asks for more`() {
        val result = score("")
        assertEquals(0, result.total)
        assertEquals(listOf(ResponseScorer.Tip.SAY_MORE), result.tips)
    }

    @Test
    fun `a full relevant answer beats a one word answer`() {
        val good = score("Wow it's been ages, I've been really busy with work lately", seconds = 4.0)
        val poor = score("Yes", seconds = 0.6)
        assertTrue("${good.total} > ${poor.total}", good.total > poor.total + 30)
    }

    @Test
    fun `multi word keywords match only as whole phrases`() {
        assertEquals(listOf("see you"), score("nice to see you").usedKeywords)
        assertEquals(emptyList<String>(), score("you see nothing").usedKeywords)
    }

    @Test
    fun `closest sample is the one with most overlap`() {
        assertEquals("Good to see you again.", score("good to see you").closestSample)
    }

    @Test
    fun `slow speech gets a pace tip`() {
        val result = score("It has been ages and I have been busy", seconds = 20.0)
        assertTrue(ResponseScorer.Tip.SPEAK_FASTER in result.tips)
    }

    @Test
    fun `missing or negative confidence does not punish`() {
        assertEquals(80, score("it has been ages", confidence = null).clarity)
        assertEquals(80, score("it has been ages", confidence = -1f).clarity)
    }

    @Test
    fun `missing duration does not crash and gives neutral fluency`() {
        val result = score("it has been ages", seconds = null)
        assertEquals(70, result.fluency)
        assertEquals(null, result.wordsPerMinute)
    }

    @Test
    fun `total stays within range for a perfect answer`() {
        val result = score("It's been ages! I've been pretty busy with work, good to see you again.", seconds = 6.0)
        assertTrue(result.total in 0..100)
        assertEquals(ResponseScorer.Tip.GREAT, result.tips.first())
    }
}
