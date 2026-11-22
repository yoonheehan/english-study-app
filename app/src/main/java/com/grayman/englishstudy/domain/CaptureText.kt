package com.grayman.englishstudy.domain

/**
 * Turns text selected in another app into words the learner can pick. Pure logic, so the
 * rules for phrases and source sentences are tested without a device.
 */
object CaptureText {
    /** A piece of the selection. Only word tokens can be picked; the rest keeps spacing and punctuation. */
    data class Token(val text: String, val start: Int, val isWord: Boolean)

    /** What was picked: the phrase to save and the sentence it came from. */
    data class Pick(val phrase: String, val sentence: String)

    /** Long selections are cut so a pasted article never floods the screen or the database. */
    const val MAX_LENGTH = 1_500

    private val WORD = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*")
    private val LINK = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    private val LINK_ANYWHERE = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val QUOTES = charArrayOf('"', '“', '”', '\'', '‘', '’')

    fun clean(raw: String): String = raw
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(*QUOTES)
        .trim()
        .take(MAX_LENGTH)

    fun isLinkOnly(text: String): Boolean = LINK.matches(text.trim())

    /** Shared text from a browser is often "title + link"; the link is kept apart as the source. */
    fun splitShared(raw: String): Pair<String, String?> {
        val link = LINK_ANYWHERE.find(raw)?.value
        val text = if (link == null) raw else raw.replace(link, " ")
        return clean(text) to link
    }

    fun tokens(text: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        WORD.findAll(text).forEach { match ->
            if (match.range.first > index) result += Token(text.substring(index, match.range.first), index, isWord = false)
            result += Token(match.value, match.range.first, isWord = true)
            index = match.range.last + 1
        }
        if (index < text.length) result += Token(text.substring(index), index, isWord = false)
        return result
    }

    /** A word or short phrase without sentence punctuation is saved as is; anything longer is a sentence to pick from. */
    fun isShortSelection(text: String): Boolean {
        val words = tokens(text).count { it.isWord }
        return words in 1..MAX_PHRASE_WORDS && text.none { it in ".!?" }
    }

    /**
     * Groups picked word tokens into phrases: words next to each other (separated only by spaces)
     * form one phrase, so picking "take" and "off" saves "take off".
     */
    fun picks(text: String, tokens: List<Token>, picked: Set<Int>): List<Pick> {
        val runs = mutableListOf<MutableList<Int>>()
        var previousWord = -1
        tokens.forEachIndexed { index, token ->
            if (!token.isWord) return@forEachIndexed
            if (index in picked) {
                val joinsPrevious = previousWord in picked &&
                    tokens.subList(previousWord + 1, index).all { it.text.isBlank() } &&
                    runs.isNotEmpty() && runs.last().last() == previousWord
                if (joinsPrevious) runs.last() += index else runs += mutableListOf(index)
            }
            previousWord = index
        }
        return runs.map { run ->
            val first = tokens[run.first()]
            val last = tokens[run.last()]
            val phrase = text.substring(first.start, last.start + last.text.length)
            Pick(normalize(phrase), sentenceAround(text, first.start))
        }
    }

    /** The sentence holding position [at], so a long selection stores only the sentence that matters. */
    fun sentenceAround(text: String, at: Int): String {
        val ends = ".!?"
        var start = at
        while (start > 0 && text[start - 1] !in ends) start--
        var end = at
        while (end < text.length && text[end] !in ends) end++
        if (end < text.length) end++
        return text.substring(start, end).trim()
    }

    /**
     * "Ubiquitous" or "Take off" at the start of a sentence is saved in lowercase.
     * Acronyms and mixed-case names (NASA, iPhone) keep their case.
     */
    fun normalize(phrase: String): String {
        val first = phrase.substringBefore(' ')
        val capitalizedOnly = first.length > 1 && first[0].isUpperCase() && first.drop(1).none { it.isUpperCase() }
        return if (capitalizedOnly) phrase.replaceFirstChar { it.lowercase() } else phrase
    }

    private const val MAX_PHRASE_WORDS = 4
}
