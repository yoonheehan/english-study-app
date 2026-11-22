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
) {
    val storyLevel: StoryLevel get() = StoryLevel.entries.firstOrNull { it.name == level } ?: StoryLevel.MEDIUM

    /** About 150 words a minute, a comfortable pace for careful reading. */
    val minutes: Int get() = maxOf(1, (words + 149) / 150)

    /** Words saved while reading this story carry this source, so the reader can highlight them. */
    val source: String get() = sourceOf(id)

    companion object {
        fun sourceOf(id: String) = "story:$id"
    }
}

@Serializable
data class StoryIndex(val source: String, val stories: List<StoryInfo>)

/** One block of a story: "h" is a heading (a fable's title, a part number), "p" a paragraph. */
@Serializable
data class StoryBlock(val t: String, val x: String) {
    val isHeading: Boolean get() = t == "h"
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
    val blocks: List<StoryBlock>,
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

    /** The block the learner was reading, so a story opens where it was left. */
    fun position(id: String): Int = prefs.getInt("pos_$id", 0)

    /** 0-100, how far into the story the learner has read. */
    fun progress(id: String): Int = prefs.getInt("pct_$id", 0)

    fun savePosition(id: String, block: Int, percent: Int) {
        prefs.edit().putInt("pos_$id", block).putInt("pct_$id", maxOf(percent, progress(id))).apply()
    }

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
