package com.grayman.englishstudy.data.words

import java.security.MessageDigest

/** The nine text fields a word card has, in the column order Word Pocket files use. */
data class WordFields(
    val word: String,
    val partOfSpeech: String = "",
    val meaning: String = "",
    val pronunciation: String = "",
    val example1: String = "",
    val example1Meaning: String = "",
    val example2: String = "",
    val example2Meaning: String = "",
    val memo: String = "",
) {
    fun asList() = listOf(word, partOfSpeech, meaning, pronunciation, example1, example1Meaning, example2, example2Meaning, memo)

    companion object {
        fun from(columns: List<String>): WordFields {
            val c = List(FIELD_COUNT) { WordFormat.clean(columns.getOrNull(it)) }
            return WordFields(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])
        }

        const val FIELD_COUNT = 9
    }
}

/** Learning state carried by a Word Pocket progress file. Times are epoch seconds, as Word Pocket stores them. */
data class WordProgress(
    val favorite: Boolean,
    val mastery: Int,
    val masteredAt: Long? = null,
    val reviewStage: Int? = null,
    val nextReviewAt: Long? = null,
    val wrongCount: Int? = null,
)

data class ProgressRow(val key: String, val fields: WordFields, val progress: WordProgress)

/** Reads and keys word files the same way Word Pocket does, so both apps agree on which word is which. */
object WordFormat {
    private val PROGRESS_MAGIC = setOf("WORD_POCKET_PROGRESS_V1", "WORD_POCKET_PROGRESS_V2", "WORDLY_PROGRESS_V1")

    fun clean(value: String?): String = value?.replace("﻿", "")?.trim().orEmpty()

    /**
     * SHA-256 over the nine fields, each followed by the unit separator 0x1F.
     * Identical to Word Pocket's source_key, which is what lets its exported progress match.
     */
    fun key(fields: WordFields): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.asList().forEach {
            digest.update(clean(it).toByteArray(Charsets.UTF_8))
            digest.update(0x1F)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isProgressFile(text: String): Boolean = clean(text.lineSequence().firstOrNull()) in PROGRESS_MAGIC

    /** Rows of a Word Pocket progress export (versions 1 and 2). Bad rows are counted, not thrown. */
    fun parseProgress(text: String): Pair<List<ProgressRow>, Int> {
        val lines = text.lineSequence().toList()
        require(clean(lines.firstOrNull()) in PROGRESS_MAGIC) { "단어포켓 학습 기록 파일이 아닙니다." }
        var invalid = 0
        val rows = lines.drop(2).filter { it.isNotBlank() }.mapNotNull { line ->
            val columns = line.split('\t')
            if (columns.size < 12 || clean(columns[1]).isEmpty()) {
                invalid++
                return@mapNotNull null
            }
            val fields = WordFields.from(columns.subList(1, 10))
            try {
                val hasReview = columns.size >= 16
                val progress = WordProgress(
                    favorite = clean(columns[10]).toInt() != 0,
                    mastery = clean(columns[11]).toInt().coerceIn(0, 100),
                    masteredAt = if (hasReview) clean(columns[12]).toLong() else null,
                    reviewStage = if (hasReview) clean(columns[13]).toInt() else null,
                    nextReviewAt = if (hasReview) clean(columns[14]).toLong() else null,
                    wrongCount = if (hasReview) clean(columns[15]).toInt() else null,
                )
                ProgressRow(clean(columns[0]).ifEmpty { key(fields) }, fields, progress)
            } catch (_: NumberFormatException) {
                invalid++
                null
            }
        }
        return rows to invalid
    }

    /**
     * A plain word list (TSV or CSV) with a header row. Supports quoted fields, so a CSV saved from
     * a spreadsheet with commas or line breaks inside a cell still reads correctly.
     */
    fun parseDelimited(text: String, delimiter: Char): Pair<List<WordFields>, Int> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endRow() {
            row += field.toString()
            field.clear()
            rows += row.toList()
            row.clear()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' && quoted -> quoted = false
                ch == '"' && field.isEmpty() -> quoted = true
                ch == delimiter && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        var invalid = 0
        val words = rows.drop(1).filter { r -> r.any { it.isNotBlank() } }.mapNotNull { r ->
            val fields = WordFields.from(r)
            if (fields.word.isEmpty() || r.size < 3) {
                invalid++
                null
            } else {
                fields
            }
        }
        return words to invalid
    }
}

/** Word Pocket's forgetting curve: a remembered word returns after 1, 3, 7, 14, 30, 60, then 120 days. */
object WordReview {
    private val INTERVAL_DAYS = intArrayOf(1, 3, 7, 14, 30, 60, 120)

    fun intervalDays(stage: Int): Int = INTERVAL_DAYS[(maxOf(1, stage) - 1).coerceAtMost(INTERVAL_DAYS.size - 1)]

    fun stageAfterSuccess(stage: Int): Int = minOf(maxOf(1, stage) + 1, INTERVAL_DAYS.size + 1)

    const val STAGE_AFTER_FAILURE = 1
}

/** Word Pocket stores parts of speech in English, sometimes combined ("verb,noun"); learners read Korean faster. */
object PartOfSpeech {
    private val KOREAN = mapOf(
        "noun" to "명사", "n" to "명사", "n." to "명사",
        "verb" to "동사", "v" to "동사", "v." to "동사",
        "adjective" to "형용사", "adj" to "형용사", "adj." to "형용사",
        "adverb" to "부사", "adv" to "부사", "adv." to "부사",
        "preposition" to "전치사", "prep" to "전치사",
        "conjunction" to "접속사", "conj" to "접속사",
        "pronoun" to "대명사", "pron" to "대명사",
        "determiner" to "한정사",
        "interjection" to "감탄사",
        "phrase" to "구", "idiom" to "관용구",
        "noun phrase" to "명사구", "verb phrase" to "동사구",
        "adjective phrase" to "형용사구", "adverb phrase" to "부사구",
        "phrasal verb" to "구동사",
        "proper noun" to "고유명사",
        "noun abbreviation" to "약어",
        "closing phrase" to "맺음말",
        "prefix" to "접두사",
        "contraction" to "축약형",
        "sentence" to "문장",
    )

    /** "verb,noun" becomes "동사·명사"; anything unknown is shown as written. */
    fun ko(value: String): String = value.split(',', '/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("·") { KOREAN[it.lowercase()] ?: it }
}
