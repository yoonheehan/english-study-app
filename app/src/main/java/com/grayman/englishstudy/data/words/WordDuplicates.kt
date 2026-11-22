package com.grayman.englishstudy.data.words

/**
 * When two cards count as the same word: the same spelling (ignoring case, extra spaces and a
 * final period) and the same part of speech. A blank part of speech matches any, because cards
 * added quickly often leave it empty. "record (n.)" and "record (v.)" stay two cards.
 * tools/dedupe-words.mjs on the PC uses the same rule.
 */
object WordDuplicates {
    private val spaces = Regex("\\s+")

    fun normalize(word: String): String = word.trim().trimEnd('.').replace(spaces, " ").lowercase()

    private fun pos(value: String) = value.trim().trimEnd('.').lowercase()

    fun sameCard(wordA: String, posA: String, wordB: String, posB: String): Boolean =
        normalize(wordA) == normalize(wordB) && (pos(posA).isEmpty() || pos(posB).isEmpty() || pos(posA) == pos(posB))

    /** Groups of two or more cards that are the same word. */
    fun <T> groups(items: List<T>, key: (T) -> Pair<String, String>): List<List<T>> =
        items.groupBy { normalize(key(it).first) }.values.filter { it.size > 1 }.flatMap { sameSpelling ->
            val left = sameSpelling.toMutableList()
            val out = mutableListOf<List<T>>()
            while (left.isNotEmpty()) {
                val first = left.removeAt(0)
                val (word, partOfSpeech) = key(first)
                val group = listOf(first) + left.filter { val (w, p) = key(it); sameCard(word, partOfSpeech, w, p) }
                left.removeAll(group.drop(1).toSet())
                if (group.size > 1) out += group
            }
            out
        }
}
