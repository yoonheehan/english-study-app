package com.grayman.speakdaily.data

import com.grayman.speakdaily.domain.ResponseScorer
import com.grayman.speakdaily.domain.TextMatcher
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the real bundled lessons, so a typo in JSON fails the build rather than a lesson. */
class ContentValidationTest {
    private val units = Level.entries.associateWith { level ->
        ContentParser.parseLessons(File("src/main/assets/content/${level.code}/lessons.json").readText())
    }
    private val lessons = units.values.flatten()

    @Test
    fun `every level has lessons and ids are unique`() {
        units.forEach { (level, list) -> assertTrue("$level is empty", list.size >= 3) }
        assertEquals(lessons.size, lessons.map { it.id }.toSet().size)
    }

    @Test
    fun `every lesson id starts with its level code`() {
        units.forEach { (level, list) ->
            list.forEach { assertTrue(it.id, it.id.startsWith("${level.code}-")) }
        }
    }

    @Test
    fun `gist questions have a valid answer`() {
        lessons.forEach {
            assertEquals(it.id, 3, it.gist.options.size)
            assertTrue(it.id, it.gist.answer in it.gist.options.indices)
        }
    }

    @Test
    fun `dictation and shadow point at real lines`() {
        lessons.forEach { lesson ->
            assertTrue(lesson.id, lesson.dictation.line in lesson.lines.indices)
            assertEquals(lesson.id, 3, lesson.dictation.distractors.size)
            assertTrue(lesson.id, lesson.shadow.isNotEmpty())
            lesson.shadow.forEach { assertTrue(lesson.id, it in lesson.lines.indices) }
        }
    }

    @Test
    fun `dictation distractors are not words of the sentence`() {
        lessons.forEach { lesson ->
            val words = TextMatcher.tokenize(lesson.lines[lesson.dictation.line].text)
            lesson.dictation.distractors.forEach {
                assertTrue("${lesson.id}: distractor '$it' is in the sentence", it.lowercase() !in words)
            }
        }
    }

    @Test
    fun `speakers are the two voices`() {
        lessons.forEach { lesson -> lesson.lines.forEach { assertTrue(lesson.id, it.speaker in 0..1) } }
    }

    @Test
    fun `response cards have several answers and keywords`() {
        lessons.forEach {
            assertTrue(it.id, it.response.answers.size >= 4)
            assertTrue(it.id, it.response.keywords.isNotEmpty())
            assertTrue(it.id, it.phrases.isNotEmpty())
        }
    }

    @Test
    fun `sample answers score well, so a model answer never looks like a failure`() {
        lessons.forEach { lesson ->
            lesson.response.answers.forEach { answer ->
                val words = TextMatcher.tokenize(answer.text).size
                val result = ResponseScorer.score(
                    ResponseScorer.Input(
                        transcript = answer.text,
                        keywords = lesson.response.keywords,
                        sampleAnswers = lesson.response.answers.map { it.text },
                        speechSeconds = words / 2.5,
                        confidence = 0.9f,
                    ),
                )
                assertEquals("${lesson.id}: ${answer.text}", 100, result.fit)
            }
        }
    }

    @Test
    fun `script gets longer as levels rise`() {
        fun average(level: Level) = units.getValue(level).flatMap { it.lines }
            .map { TextMatcher.tokenize(it.text).size }.average()
        assertTrue(average(Level.A1) < average(Level.B1))
        assertTrue(average(Level.B1) < average(Level.C1))
    }
}
