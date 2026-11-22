package com.grayman.englishstudy.data

import com.grayman.englishstudy.domain.ListeningTestBuilder
import com.grayman.englishstudy.domain.TestLength
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the real bundled listening tests, so a broken question fails the build, not the test. */
class ListeningContentTest {
    private val pools = Level.entries.associateWith { level ->
        ListeningParser.parse(File("src/main/assets/content/${level.code}/listening-tests.json").readText())
    }

    @Test
    fun `every level can build a full twenty question test`() {
        pools.forEach { (level, pool) ->
            val pages = ListeningTestBuilder.build(pool, TestLength.FULL, random = Random(level.ordinal))
            assertEquals("$level", 20, pages.sumOf { it.questionCount })
        }
    }

    @Test
    fun `ids are unique and start with the level code`() {
        val ids = pools.flatMap { (level, pool) ->
            (pool.qr.map { it.id } + pool.sets.map { it.id }).onEach {
                assertTrue(it, it.startsWith("${level.code}-"))
            }
        }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `response items have three options and a valid answer`() {
        pools.values.flatMap { it.qr }.forEach {
            assertEquals(it.id, 3, it.options.size)
            assertTrue(it.id, it.answer in it.options.indices)
            assertEquals(it.id, it.options.size, it.options.map { o -> o.text }.toSet().size)
        }
    }

    @Test
    fun `sets have three questions with four options each`() {
        pools.values.flatMap { it.sets }.forEach { set ->
            assertEquals(set.id, 3, set.questions.size)
            assertTrue(set.id, set.lines.isNotEmpty())
            set.questions.forEach {
                assertEquals(set.id, 4, it.options.size)
                assertTrue(set.id, it.answer in it.options.indices)
            }
            if (set.type == SetType.TALK) assertTrue(set.id, set.lines.all { it.speaker == 0 })
            if (set.type == SetType.CONVERSATION) assertEquals(set.id, setOf(0, 1), set.lines.map { it.speaker }.toSet())
        }
    }

    /** A test where the answer is usually A can be passed without listening. */
    @Test
    fun `answer positions are spread out`() {
        pools.forEach { (level, pool) ->
            val qrCounts = pool.qr.groupingBy { it.answer }.eachCount()
            assertTrue("$level qr $qrCounts", (0..2).all { (qrCounts[it] ?: 0) >= 2 })
            val setAnswers = pool.sets.flatMap { s -> s.questions.map { it.answer } }.toSet()
            assertTrue("$level sets $setAnswers", setAnswers.size == 4)
        }
    }

    @Test
    fun `recordings get longer as levels rise`() {
        fun words(level: Level) = pools.getValue(level).sets.flatMap { it.lines }
            .sumOf { it.text.split(" ").size }.toDouble() / pools.getValue(level).sets.size
        assertTrue(words(Level.A1) < words(Level.B1))
        assertTrue(words(Level.B1) < words(Level.C1))
    }
}
