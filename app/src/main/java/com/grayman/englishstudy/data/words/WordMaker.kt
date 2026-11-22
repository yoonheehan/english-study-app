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
        MEANING: the Korean meaning as used in the sentence first, then one or two other common Korean meanings, separated by commas
        SENTENCE_KO: a natural Korean translation of the sentence (write - if there is no sentence)
        EXAMPLE: a new short, natural English example sentence that uses the word in the same meaning
        EXAMPLE_KO: a natural Korean translation of that example
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
            meaning = v("MEANING"),
            sentenceKo = v("SENTENCE_KO"),
            example = v("EXAMPLE"),
            exampleKo = v("EXAMPLE_KO"),
        )
    }

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
