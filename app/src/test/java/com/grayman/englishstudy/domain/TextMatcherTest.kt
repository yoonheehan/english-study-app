package com.grayman.englishstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMatcherTest {

    @Test
    fun `identical text scores 100 regardless of case and punctuation`() {
        val result = TextMatcher.compare("I'd like a coffee, please.", "i'd LIKE a coffee please")
        assertEquals(100, result.score)
        assertEquals(emptyList<String>(), result.missing)
        assertEquals(emptyList<String>(), result.extra)
    }

    @Test
    fun `curly apostrophe matches straight apostrophe`() {
        assertEquals(100, TextMatcher.compare("I'd go", "I’d go").score)
    }

    @Test
    fun `one missing word does not fail the words after it`() {
        val result = TextMatcher.compare("a large black coffee", "a large coffee")
        assertEquals(75, result.score)
        assertEquals(listOf("black"), result.missing)
        assertEquals(listOf("a", "large", "coffee"), result.matched)
    }

    @Test
    fun `extra and substituted words are reported`() {
        val result = TextMatcher.compare("ship the box", "sheep the big box")
        assertEquals(66, result.score)
        assertEquals(listOf("ship"), result.missing)
        assertEquals(listOf("sheep", "big"), result.extra)
    }

    @Test
    fun `wrong word order loses points`() {
        assertEquals(50, TextMatcher.compare("one two", "two one").score)
    }

    @Test
    fun `empty inputs do not crash`() {
        assertEquals(0, TextMatcher.compare("", "hello").score)
        assertEquals(0, TextMatcher.compare("hello", "").score)
        assertEquals(listOf("hello"), TextMatcher.compare("hello", "").missing)
    }
}
