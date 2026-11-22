package com.grayman.englishstudy.domain

import java.util.Locale

/** Grades written answers in the word test. Same rules as Word Pocket's AnswerMatcher. */
object AnswerMatcher {
    private val KOREAN_SUFFIXES = listOf(
        "시키다", "스럽다", "적으로", "하다", "되다", "이다", "적인", "하게", "하는", "스런",
        "한", "된", "적", "의", "히", "게",
    )

    /** English: spelling must be exact. "color/colour" style answers accept any one form. */
    fun matchesEnglish(answer: String, expected: String): Boolean {
        val a = normalizeEnglish(answer)
        if (a.isEmpty()) return false
        if (a == normalizeEnglish(expected)) return true
        return expected.split(',', ';', '/').any { option ->
            val o = normalizeEnglish(option)
            o.isNotEmpty() && o == a
        }
    }

    /**
     * Korean meaning: equal to one of the listed meanings, differing only in the ending
     * (행복한 ↔ 행복하다), or one letter off in a long meaning (a handwriting slip).
     * Matching only part of a meaning is not enough.
     */
    fun matchesMeaning(answer: String, expected: String): Boolean {
        val whole = normalizeMeaning(answer)
        if (whole.isEmpty()) return false
        if (whole == normalizeMeaning(expected)) return true
        val choices = meaningChoices(expected)
        return answer.split(',', ';', '/', '·').any { part ->
            val a = normalizeMeaning(part)
            a.isNotEmpty() && choices.any { c ->
                a == c ||
                    stem(a).let { it.length >= 2 && it == stem(c) } ||
                    (c.length >= 4 && kotlin.math.abs(c.length - a.length) <= 1 && editDistance(a, c) <= 1)
            }
        }
    }

    internal fun meaningChoices(expected: String): List<String> {
        val result = LinkedHashSet<String>()
        fun add(value: String) = normalizeMeaning(value).takeIf { it.isNotEmpty() }?.let { result.add(it) }
        val withoutNumbers = expected.replace(Regex("[0-9]+[.)]"), ",")
        withoutNumbers.split(Regex("[,;/·\\n]")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { segment ->
            add(segment.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), ""))
            add(segment)
            segment.split(Regex("[()\\[\\]]")).forEach(::add)
            // "~보다 많다", "~을 이용하다": the placeholder and the particle after it may be left
            // out, since "~" is awkward to write and handwriting rarely reads it.
            if (TILDE.containsMatchIn(segment)) add(segment.replace(TILDE_WITH_PARTICLE, " "))
        }
        return result.toList()
    }

    internal fun stem(value: String): String {
        val suffix = KOREAN_SUFFIXES.firstOrNull { value.length > it.length && value.endsWith(it) } ?: return value
        return value.dropLast(suffix.length)
    }

    internal fun normalizeEnglish(value: String): String = value.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    internal fun normalizeMeaning(value: String): String =
        value.lowercase(Locale.KOREA).replace(Regex("[\\s\\p{Punct}·~～∼〜…‘’“”]"), "")

    private val TILDE = Regex("[~～∼〜]")
    private val TILDE_WITH_PARTICLE =
        Regex("[~～∼〜]\\s*(에게서|에게|에서|으로|보다|하고|부터|까지|을|를|이|가|은|는|에|와|과|의|로|도|만)?\\s*")

    internal fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current.also { current = previous }
        }
        return previous[b.length]
    }
}
