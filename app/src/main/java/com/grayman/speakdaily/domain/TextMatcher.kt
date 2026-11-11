package com.grayman.speakdaily.domain

/**
 * Compares a recognized transcript with the expected sentence word by word.
 * Uses the longest common subsequence, so word order matters and a single
 * missed word does not fail every word after it.
 */
object TextMatcher {

    data class Result(
        val score: Int,
        val matched: List<String>,
        val missing: List<String>,
        val extra: List<String>,
    )

    fun compare(expected: String, actual: String): Result {
        val want = tokenize(expected)
        val got = tokenize(actual)
        if (want.isEmpty()) return Result(0, emptyList(), emptyList(), got)

        val table = Array(want.size + 1) { IntArray(got.size + 1) }
        for (i in want.indices.reversed()) {
            for (j in got.indices.reversed()) {
                table[i][j] = if (want[i] == got[j]) {
                    table[i + 1][j + 1] + 1
                } else {
                    maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
        }

        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val extra = mutableListOf<String>()
        var i = 0
        var j = 0
        while (i < want.size && j < got.size) {
            when {
                want[i] == got[j] -> {
                    matched += want[i]
                    i++
                    j++
                }
                table[i + 1][j] >= table[i][j + 1] -> missing += want[i++]
                else -> extra += got[j++]
            }
        }
        missing += want.drop(i)
        extra += got.drop(j)

        return Result(matched.size * 100 / want.size, matched, missing, extra)
    }

    /** Lowercases, keeps apostrophes inside words, and drops other punctuation. */
    fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace('’', '\'')
            .split(Regex("[^a-z0-9']+"))
            .map { it.trim('\'') }
            .filter { it.isNotEmpty() }
}
