package com.grayman.englishstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTextTest {
    private val sentence = "Smartphones have become ubiquitous. The rocket will take off at dawn."

    private fun pick(text: String, vararg words: String): List<CaptureText.Pick> {
        val tokens = CaptureText.tokens(text)
        val wanted = words.toList()
        val picked = tokens.withIndex().filter { (_, t) -> t.isWord && t.text in wanted }.map { it.index }.toSet()
        return CaptureText.picks(text, tokens, picked)
    }

    @Test
    fun `clean collapses spaces and strips quotes`() {
        assertEquals("ubiquitous", CaptureText.clean("  “ubiquitous” \n"))
        assertEquals("a b", CaptureText.clean("a\n\n  b"))
    }

    @Test
    fun `very long selections are cut`() {
        assertEquals(CaptureText.MAX_LENGTH, CaptureText.clean("a".repeat(5_000)).length)
    }

    @Test
    fun `links are recognised and split from shared text`() {
        assertTrue(CaptureText.isLinkOnly("https://en.wikipedia.org/wiki/Serendipity"))
        assertFalse(CaptureText.isLinkOnly("serendipity"))
        val (text, link) = CaptureText.splitShared("Serendipity - Wikipedia https://en.wikipedia.org/wiki/Serendipity")
        assertEquals("Serendipity - Wikipedia", text)
        assertEquals("https://en.wikipedia.org/wiki/Serendipity", link)
        assertNull(CaptureText.splitShared("no link here").second)
    }

    @Test
    fun `tokens keep contractions and hyphens together`() {
        val words = CaptureText.tokens("It's a well-known fact.").filter { it.isWord }.map { it.text }
        assertEquals(listOf("It's", "a", "well-known", "fact"), words)
    }

    @Test
    fun `short selections are saved directly, sentences are picked from`() {
        assertTrue(CaptureText.isShortSelection("ubiquitous"))
        assertTrue(CaptureText.isShortSelection("take off"))
        assertFalse(CaptureText.isShortSelection("The rocket will take off."))
        assertFalse(CaptureText.isShortSelection("one two three four five"))
        assertFalse(CaptureText.isShortSelection("..."))
    }

    @Test
    fun `adjacent picked words become one phrase with its own sentence`() {
        val picks = pick(sentence, "take", "off")
        assertEquals(listOf(CaptureText.Pick("take off", "The rocket will take off at dawn.")), picks)
    }

    @Test
    fun `separate words become separate picks`() {
        val picks = pick(sentence, "ubiquitous", "dawn")
        assertEquals(listOf("ubiquitous", "dawn"), picks.map { it.phrase })
        assertEquals("Smartphones have become ubiquitous.", picks[0].sentence)
    }

    @Test
    fun `punctuation between words breaks a phrase`() {
        assertEquals(listOf("ubiquitous", "the"), pick(sentence, "ubiquitous", "The").map { it.phrase })
    }

    @Test
    fun `capitalised words at sentence start are lowercased but names and acronyms are kept`() {
        assertEquals("smartphones", CaptureText.normalize("Smartphones"))
        assertEquals("NASA", CaptureText.normalize("NASA"))
        assertEquals("iPhone", CaptureText.normalize("iPhone"))
        assertEquals("take off", CaptureText.normalize("Take off"))
    }

    @Test
    fun `sentence without end punctuation is the whole text`() {
        assertEquals("just a fragment", CaptureText.sentenceAround("just a fragment", 5))
    }
}
