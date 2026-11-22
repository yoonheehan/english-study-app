package com.grayman.englishstudy.data.words

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormatTest {
    private val bundled = File("src/main/assets/words/initial_words.tsv").readText()

    @Test
    fun `key matches Word Pocket's source_key for the first bundled word`() {
        val (words, _) = WordFormat.parseDelimited(bundled, '\t')
        // Reference value computed with Word Pocket's algorithm from the same file.
        assertEquals("competent", words.first().word)
        assertEquals("8821e3e60d474d264db29b62270de1ddbaf14bd8056a7847c2bac2e20af6d593", WordFormat.key(words.first()))
    }

    @Test
    fun `all bundled words parse and have distinct keys`() {
        val (words, invalid) = WordFormat.parseDelimited(bundled, '\t')
        assertEquals(2900, words.size)
        assertEquals(0, invalid)
        assertEquals(words.size, words.map { WordFormat.key(it) }.toSet().size)
    }

    @Test
    fun `key ignores surrounding spaces and byte order marks, like Word Pocket`() {
        assertEquals(
            WordFormat.key(WordFields("apple", meaning = "사과")),
            WordFormat.key(WordFields.from(listOf("﻿ apple ", "", " 사과"))),
        )
    }

    @Test
    fun `csv keeps commas, quotes, and line breaks inside quoted cells`() {
        val csv = "word,pos,meaning\r\n\"set\",verb,\"놓다, 두다\"\n\"say \"\"hi\"\"\",phrase,\"인사\n하다\"\n"
        val (words, invalid) = WordFormat.parseDelimited(csv, ',')
        assertEquals(0, invalid)
        assertEquals("놓다, 두다", words[0].meaning)
        assertEquals("say \"hi\"", words[1].word)
        assertEquals("인사\n하다", words[1].meaning)
    }

    @Test
    fun `rows without a word or with too few columns are counted as invalid`() {
        val (words, invalid) = WordFormat.parseDelimited("h1\th2\th3\n\tverb\tx\nonly\ttwo\nok\tnoun\t좋음\n", '\t')
        assertEquals(listOf("ok"), words.map { it.word })
        assertEquals(2, invalid)
    }

    @Test
    fun `progress v2 keeps review schedule`() {
        val text = "WORD_POCKET_PROGRESS_V2\nheader\n" +
            "abc\tapple\tnoun\t사과\t\t\t\t\t\t\t1\t100\t1700000000\t3\t1700600000\t2\n"
        assertTrue(WordFormat.isProgressFile(text))
        val (rows, invalid) = WordFormat.parseProgress(text)
        assertEquals(0, invalid)
        val row = rows.single()
        assertEquals("abc", row.key)
        assertEquals("apple", row.fields.word)
        assertEquals(WordProgress(true, 100, 1700000000, 3, 1700600000, 2), row.progress)
    }

    @Test
    fun `progress v1 has no schedule and missing key falls back to the computed key`() {
        val text = "WORD_POCKET_PROGRESS_V1\nheader\n\tapple\tnoun\t사과\t\t\t\t\t\t\t0\t40\n"
        val row = WordFormat.parseProgress(text).first.single()
        assertEquals(WordFormat.key(WordFields("apple", "noun", "사과")), row.key)
        assertNull(row.progress.reviewStage)
        assertEquals(40, row.progress.mastery)
    }

    @Test
    fun `bad numbers in progress rows are skipped, not fatal`() {
        val text = "WORD_POCKET_PROGRESS_V1\nheader\nk\tapple\t\t\t\t\t\t\t\t\tyes\t40\n"
        val (rows, invalid) = WordFormat.parseProgress(text)
        assertTrue(rows.isEmpty())
        assertEquals(1, invalid)
    }

    @Test
    fun `plain word list is not mistaken for a progress file`() {
        assertFalse(WordFormat.isProgressFile(bundled))
    }

    @Test
    fun `review intervals follow Word Pocket's forgetting curve`() {
        assertEquals(listOf(1, 3, 7, 14, 30, 60, 120, 120), (1..8).map { WordReview.intervalDays(it) })
        assertEquals(1, WordReview.intervalDays(0))
        assertEquals(8, WordReview.stageAfterSuccess(7))
        assertEquals(8, WordReview.stageAfterSuccess(8))
    }
}

class PartOfSpeechTest {
    @Test
    fun `single and combined parts of speech read in Korean`() {
        assertEquals("명사", PartOfSpeech.ko("noun"))
        assertEquals("명사구", PartOfSpeech.ko("noun phrase"))
        assertEquals("동사·명사", PartOfSpeech.ko("verb,noun"))
        assertEquals("형용사·부사", PartOfSpeech.ko("adjective/adverb"))
        assertEquals("Mystery", PartOfSpeech.ko("Mystery"))
        assertEquals("", PartOfSpeech.ko(""))
    }

    @Test
    fun `every part of speech in the bundled list has a Korean name`() {
        val (words, _) = WordFormat.parseDelimited(java.io.File("src/main/assets/words/initial_words.tsv").readText(), '\t')
        val untranslated = words.map { it.partOfSpeech }.distinct().filter { pos ->
            pos.split(',', '/').any { part -> PartOfSpeech.ko(part) == part.trim() && part.isNotBlank() }
        }
        assertEquals(emptyList<String>(), untranslated)
    }
}
