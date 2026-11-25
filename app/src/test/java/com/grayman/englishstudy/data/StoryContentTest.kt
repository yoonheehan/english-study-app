package com.grayman.englishstudy.data

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryContentTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File("src/main/assets/stories")
    private val index = json.decodeFromString(StoryIndex.serializer(), File(dir, "index.json").readText())

    @Test fun everyStoryInTheIndexLoads() {
        assertTrue(index.stories.size >= 14)
        assertEquals(22, index.stories.first { it.id == "aesop" }.chapters)
        assertEquals(index.stories.size, index.stories.map { it.id }.toSet().size)
        StoryLevel.entries.forEach { level -> assertTrue("no $level story", index.stories.any { it.storyLevel == level }) }
        index.stories.forEach { info ->
            val story = json.decodeFromString(Story.serializer(), File(dir, "${info.id}.json").readText())
            assertEquals(info.id, story.id)
            assertEquals(info.chapters, story.chapters.size)
            assertTrue("${info.id} has an empty chapter", story.chapters.all { c -> c.blocks.count { !it.isHeading } >= 1 && c.title.isNotBlank() })
            assertEquals(info.words, story.chapters.sumOf { it.words })
            // Chapters are one sitting each: none is a wall of text.
            assertTrue("${info.id} has a chapter that is too long", story.chapters.all { it.words <= 1300 })
        }
    }

    @Test fun storiesCarryNoLicenseTextOrMarkup() {
        index.stories.forEach { info ->
            val text = File(dir, "${info.id}.json").readText()
            assertFalse("${info.id} has Gutenberg text", text.contains("Project Gutenberg") || text.contains("*** START"))
            assertFalse("${info.id} has picture notes", text.contains("[Picture") || text.contains("[Illustration"))
            assertFalse("${info.id} has underscores", text.contains("_"))
        }
    }
}
