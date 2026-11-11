package com.grayman.speakdaily.domain

/**
 * Partial scoring without AI. It can see whether the answer fits the situation's
 * keywords, resembles the sample answers, and how the speech flowed. It cannot judge
 * grammar, so the screen labels this result as partial.
 */
object ResponseScorer {

    data class Input(
        val transcript: String,
        val keywords: List<String>,
        val sampleAnswers: List<String>,
        val speechSeconds: Double?,
        val confidence: Float?,
    )

    enum class Tip { SAY_MORE, USE_KEYWORD, SPEAK_FASTER, SLOW_DOWN, CLEARER, GREAT }

    data class Result(
        val total: Int,
        val fit: Int,
        val expression: Int,
        val fluency: Int,
        val clarity: Int,
        val wordsPerMinute: Int?,
        val closestSample: String?,
        val usedKeywords: List<String>,
        val tips: List<Tip>,
    )

    fun score(input: Input): Result {
        val words = TextMatcher.tokenize(input.transcript)
        if (words.isEmpty()) {
            return Result(0, 0, 0, 0, 0, null, null, emptyList(), listOf(Tip.SAY_MORE))
        }

        val spoken = " ${words.joinToString(" ")} "
        val used = input.keywords.filter { keyword ->
            val phrase = TextMatcher.tokenize(keyword).joinToString(" ")
            phrase.isNotEmpty() && " $phrase " in spoken
        }

        var closest: String? = null
        var bestOverlap = 0.0
        val spokenSet = words.toSet()
        for (sample in input.sampleAnswers) {
            val sampleSet = TextMatcher.tokenize(sample).toSet()
            if (sampleSet.isEmpty()) continue
            val overlap = (spokenSet intersect sampleSet).size.toDouble() / sampleSet.size
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                closest = sample
            }
        }

        // One keyword shows the answer fits. A sample overlap also counts, because
        // the learner may have answered correctly with other words.
        val keywordFit = if (input.keywords.isEmpty()) 100 else minOf(100, 60 + used.size * 40).takeIf { used.isNotEmpty() } ?: 30
        val fit = maxOf(keywordFit, (bestOverlap * 100).toInt())

        val expression = when {
            words.size >= 12 -> 100
            words.size >= 7 -> 85
            words.size >= 4 -> 65
            words.size >= 2 -> 45
            else -> 25
        }

        val wpm = input.speechSeconds?.takeIf { it > 0.3 }?.let { (words.size * 60 / it).toInt() }
        val fluency = when {
            wpm == null -> 70
            wpm in 90..190 -> 100
            wpm in 60..89 || wpm in 191..230 -> 75
            else -> 50
        }

        // Some recognizers report no confidence or -1; do not punish the learner for that.
        val clarity = input.confidence?.takeIf { it >= 0f }?.let { (it * 100).toInt().coerceIn(0, 100) } ?: 80

        val total = (fit * 0.35 + expression * 0.30 + fluency * 0.20 + clarity * 0.15).toInt().coerceIn(0, 100)

        val tips = buildList {
            if (words.size < 4) add(Tip.SAY_MORE)
            if (used.isEmpty() && input.keywords.isNotEmpty() && bestOverlap < 0.5) add(Tip.USE_KEYWORD)
            if (wpm != null && wpm < 90) add(Tip.SPEAK_FASTER)
            if (wpm != null && wpm > 190) add(Tip.SLOW_DOWN)
            if (clarity < 60) add(Tip.CLEARER)
            if (isEmpty()) add(Tip.GREAT)
        }

        return Result(total, fit, expression, fluency, clarity, wpm, closest, used, tips.take(2))
    }
}
