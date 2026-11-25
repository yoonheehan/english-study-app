package com.grayman.englishstudy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How hard a story is to read. */
enum class StoryLevel(val labelKo: String, val guideKo: String) {
    EASY("쉬움", "짧은 우화와 동화. 문장이 짧아요."),
    MEDIUM("보통", "유명한 단편과 소설 한 장. 대화가 많아요."),
    HARD("어려움", "셜록 홈즈. 길고 옛날식 표현이 많아요."),
}

@Serializable
data class StoryInfo(
    val id: String,
    val title: String,
    val titleKo: String,
    val author: String,
    val year: Int,
    val level: String,
    val words: Int,
    val note: String = "",
    /** How many chapters (short sections, read one at a time) the story has. */
    val chapters: Int = 1,
) {
    val storyLevel: StoryLevel get() = StoryLevel.entries.firstOrNull { it.name == level } ?: StoryLevel.MEDIUM

    /** About 150 words a minute, a comfortable pace for careful reading. */
    val minutes: Int get() = maxOf(1, (words + 149) / 150)

    /** Words saved while reading this story carry this source, so the reader can highlight them. */
    val source: String get() = sourceOf(id)

    companion object {
        fun sourceOf(id: String) = "story:$id"

        /** Sources to highlight saved words from, including stories that were one file before chapters. */
        fun sourcesOf(id: String): List<String> = listOf(sourceOf(id)) + when (id) {
            "aesop" -> listOf("aesop-1", "aesop-2")
            "alice" -> listOf("alice-1", "alice-2", "alice-3")
            else -> emptyList()
        }.map(::sourceOf)
    }
}

@Serializable
data class StoryIndex(val source: String, val stories: List<StoryInfo>)

/** One block of a story: "h" is a heading (a fable's title, a part number), "p" a paragraph. */
@Serializable
data class StoryBlock(val t: String, val x: String) {
    val isHeading: Boolean get() = t == "h"
}

/** One sitting's worth of a story: a fable, or 600-1100 words of a longer one. */
@Serializable
data class StoryChapter(val title: String, val blocks: List<StoryBlock>) {
    val words: Int get() = blocks.filter { !it.isHeading }.sumOf { it.x.split(" ").size }
}

@Serializable
data class Story(
    val id: String,
    val title: String,
    val titleKo: String,
    val author: String,
    val year: Int,
    val level: String,
    val words: Int,
    val chapters: List<StoryChapter>,
)

/**
 * Public-domain stories bundled in assets/stories (from Project Gutenberg, header and license
 * removed; see tools in the scratch scripts). Reading position is kept per story on this device.
 */
class StoryLibrary(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    val index: StoryIndex by lazy {
        json.decodeFromString(StoryIndex.serializer(), context.assets.open("stories/index.json").bufferedReader().use { it.readText() })
    }

    fun info(id: String): StoryInfo? = index.stories.firstOrNull { it.id == id }

    fun load(id: String): Story =
        json.decodeFromString(Story.serializer(), context.assets.open("stories/$id.json").bufferedReader().use { it.readText() })

    /** The paragraph the learner was reading in a chapter, so it opens where it was left. */
    fun position(id: String, chapter: Int): Int = prefs.getInt("pos_${id}_$chapter", 0)

    fun savePosition(id: String, chapter: Int, block: Int) {
        prefs.edit().putInt("pos_${id}_$chapter", block).putInt("last_$id", chapter).apply()
    }

    /** The chapter opened last, to continue from. */
    fun lastChapter(id: String): Int = prefs.getInt("last_$id", 0)

    /** Chapters read to the end. */
    fun readChapters(id: String): Set<Int> =
        prefs.getString("read_$id", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun markRead(id: String, chapter: Int) {
        val read = readChapters(id) + chapter
        prefs.edit().putString("read_$id", read.sorted().joinToString(",")).apply()
    }

    /** 0-100, how many of the story's chapters have been read. */
    fun progress(info: StoryInfo): Int = readChapters(info.id).size * 100 / info.chapters.coerceAtLeast(1)

    var textSize: Int
        get() = prefs.getInt("text_size", 20)
        set(value) = prefs.edit().putInt("text_size", value).apply()
}

/** Finds the word around a tapped character, for the reader. Pure, so it is unit tested. */
object StoryText {
    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == '-'

    /** The word's start and end (exclusive) around [offset], or null when a space or mark was tapped. */
    fun wordAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        var i = offset.coerceIn(0, text.length - 1)
        if (!isWordChar(text[i])) {
            // A tap just after the last letter still means that word.
            if (i > 0 && isWordChar(text[i - 1])) i-- else return null
        }
        var start = i
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = i + 1
        while (end < text.length && isWordChar(text[end])) end++
        // Trim quotes and dashes at the edges ("’Tis", "well-" at a line end, possessive ’s stays).
        while (start < end && !text[start].isLetter()) start++
        while (end > start && !text[end - 1].isLetter()) end--
        if (start >= end) return null
        return start until end
    }

    /** The word as it should be looked up: possessive endings dropped ("Prince’s" -> "Prince"). */
    fun lookupForm(word: String): String =
        word.replace('’', '\'').removeSuffix("'s").removeSuffix("'S").trim('\'', '-')
}
