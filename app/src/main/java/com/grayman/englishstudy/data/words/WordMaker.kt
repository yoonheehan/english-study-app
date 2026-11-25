package com.grayman.englishstudy.data.words

/**
 * Builds a word card from a word the learner tapped and the sentence it was in.
 * The translator gives a quick meaning at once; when the on-device AI is downloaded it fills the
 * dictionary form, part of speech, pronunciation, the meaning in this sentence and a second example.
 * The sentence itself becomes the first example, so the card remembers where the word was met.
 */
object WordMaker {
    /** What the AI filled in. Any field may be blank when the reply missed it. */
    data class AiFill(
        val lemma: String,
        val partOfSpeech: String,
        val pronunciation: String,
        val meaning: String,
        val sentenceKo: String,
        val example: String,
        val exampleKo: String,
    )

    fun aiPrompt(word: String, sentence: String): String = """
        You are making a vocabulary card for a Korean learner of English.
        Word: "$word"
        ${if (sentence.isNotBlank()) "Sentence where the learner found it: \"$sentence\"" else "There is no sentence; use the most common meaning."}
        Reply with exactly these seven lines and nothing else:
        LEMMA: the dictionary form of the word (for example "ran" -> "run", "boxes" -> "box"; keep phrases as they are)
        POS: one of noun, verb, adjective, adverb, preposition, conjunction, pronoun, phrase
        IPA: American pronunciation in slashes
        MEANING: one to three short Korean dictionary meanings, the one used in the sentence first, separated by commas; each a few words at most, no explanations, no repeats (e.g. "극심한 고통" or "(양·새의) 떼, 무리")
        SENTENCE_KO: a natural Korean translation of the sentence, in Korean only (write - if there is no sentence)
        EXAMPLE: a new short, everyday English sentence that uses the word in the same meaning
        EXAMPLE_KO: a natural Korean translation of that example, in Korean only
    """.trimIndent()

    private val keys = listOf("LEMMA", "POS", "IPA", "MEANING", "SENTENCE_KO", "EXAMPLE", "EXAMPLE_KO")

    /** Reads the seven lines; null when the reply has none of them. */
    fun parse(reply: String): AiFill? {
        val values = mutableMapOf<String, String>()
        reply.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('*', '-', ' ').replace("**", "")
            val key = keys.firstOrNull { line.startsWith("$it:", ignoreCase = true) || line.startsWith("$it :", ignoreCase = true) } ?: return@forEach
            if (key !in values) values[key] = line.substringAfter(':').trim().trim('"')
        }
        if (values.isEmpty()) return null
        fun v(key: String) = values[key].orEmpty().takeUnless { it == "-" }.orEmpty()
        val pos = v("POS").lowercase().let { p -> POS.firstOrNull { p.startsWith(it) } ?: "" }
        val ipa = v("IPA").let { if (it.isNotEmpty() && !it.startsWith("/")) "/${it.trim('/')}/" else it }
        return AiFill(
            lemma = v("LEMMA").takeIf { it.isNotEmpty() && it.length <= 40 }.orEmpty(),
            partOfSpeech = pos,
            pronunciation = ipa,
            meaning = tidyMeaning(v("MEANING")),
            sentenceKo = v("SENTENCE_KO").takeIf(::looksLikeKorean).orEmpty(),
            example = v("EXAMPLE"),
            exampleKo = v("EXAMPLE_KO").takeIf(::looksLikeKorean).orEmpty(),
        )
    }

    /**
     * Keeps a meaning short and clear: at most three parts, none repeated or contained in another
     * ("극심한 고통, 참혹함, 극도의 공포, 심한 고통" becomes "극심한 고통, 참혹함, 극도의 공포").
     */
    fun tidyMeaning(meaning: String): String {
        val kept = mutableListOf<String>()
        meaning.split(',', ';', '/').map { it.trim().trim('.', '"') }.filter { it.isNotEmpty() }.forEach { part ->
            val c = part.replace(squeeze, "")
            val dup = kept.any { k -> val kc = k.replace(squeeze, ""); kc.contains(c) || c.contains(kc) }
            if (!dup && kept.size < MAX_MEANINGS) kept += part
        }
        return kept.joinToString(", ")
    }

    /** A translation the small model garbled: English left in it, or the same syllables repeated. */
    fun looksLikeKorean(text: String): Boolean {
        if (text.isBlank() || Regex("[A-Za-z]{3,}").containsMatchIn(text)) return false
        val hangul = text.count { it in '가'..'힣' }
        if (hangul < text.count { !it.isWhitespace() } / 2) return false
        return !repeated.containsMatchIn(text)
    }

    private val squeeze = Regex("""[\s()·~]""")
    private val repeated = Regex("""(\S{2,})\s*\1""")

    private const val MAX_MEANINGS = 3

    private val POS = listOf("noun", "verb", "adjective", "adverb", "preposition", "conjunction", "pronoun", "phrase")

    /**
     * The card to save. The tapped word is kept unless the AI gave a dictionary form that is
     * plausibly the same word (it shares its first letters), so a confused reply cannot rename it.
     */
    fun fields(
        tapped: String,
        sentence: String,
        quickMeaning: String,
        quickSentenceKo: String,
        ai: AiFill?,
        editedMeaning: String? = null,
    ): WordFields {
        val word = ai?.lemma?.takeIf { sameStem(it, tapped) } ?: tapped.trim()
        val meaning = editedMeaning?.takeIf { it.isNotBlank() } ?: ai?.meaning?.takeIf { it.isNotBlank() } ?: quickMeaning
        val hasSentence = sentence.isNotBlank()
        return WordFields(
            word = word,
            partOfSpeech = ai?.partOfSpeech.orEmpty(),
            meaning = meaning.trim(),
            pronunciation = ai?.pronunciation.orEmpty(),
            example1 = if (hasSentence) sentence.trim() else ai?.example.orEmpty(),
            example1Meaning = if (hasSentence) (ai?.sentenceKo?.takeIf { it.isNotBlank() } ?: quickSentenceKo) else ai?.exampleKo.orEmpty(),
            example2 = if (hasSentence) ai?.example.orEmpty() else "",
            example2Meaning = if (hasSentence) ai?.exampleKo.orEmpty() else "",
            memo = "",
        )
    }

    private fun sameStem(lemma: String, tapped: String): Boolean {
        val a = lemma.trim().lowercase()
        val b = tapped.trim().lowercase()
        if (a.isEmpty() || a.contains('\n')) return false
        val n = minOf(3, a.length, b.length)
        return a.take(n) == b.take(n) || a.split(' ').size == b.split(' ').size && a.first() == b.first()
    }

    /** The sentence around [index] in [text], for a word tapped in a paragraph. */
    fun sentenceAround(text: String, index: Int): String {
        if (text.isEmpty()) return ""
        val i = index.coerceIn(0, text.length - 1)
        val enders = charArrayOf('.', '!', '?', '\n')
        var start = text.lastIndexOfAny(enders, i - 1).let { if (it < 0) 0 else it + 1 }
        var end = text.indexOfAny(enders, i).let { if (it < 0) text.length else it + 1 }
        // Do not cut after "Mr." or "Mrs." and the like.
        while (start > 1 && text.substring(maxOf(0, start - 4), start).trimStart().let { it == "Mr." || it == "Mrs." || it == "Dr." || it == "St." || it == "Ms." }) {
            start = text.lastIndexOfAny(enders, start - 2).let { if (it < 0) 0 else it + 1 }
        }
        while (end < text.length && text.substring(maxOf(0, end - 3), end).let { it.endsWith("Mr.") || it.endsWith("Dr.") || it.endsWith("Ms.") || it.endsWith("St.") } ) {
            end = text.indexOfAny(enders, end).let { if (it < 0) text.length else it + 1 }
        }
        return text.substring(start, end).trim().trim('"', '“', '”').trim()
    }
}
