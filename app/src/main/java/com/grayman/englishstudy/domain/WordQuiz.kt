package com.grayman.englishstudy.domain

import kotlinx.serialization.Serializable

/** Which words a test draws from. */
@Serializable
enum class StudySource(val labelKo: String, val descriptionKo: String) {
    NEW("새 단어", "아직 외우지 않은 단어에서 골라요. 읽다가 넣은 단어가 먼저 나와요."),
    DUE("복습할 단어", "외운 단어 중 오늘 다시 볼 날이 된 단어예요."),
    WRONG("자주 틀린 단어", "많이 틀린 단어부터 나와요."),
    FAVORITE("즐겨찾기", "별을 눌러 모은 단어에서 무작위로 골라요."),
}

/** What the learner writes. BOTH is Word Pocket's two rounds: meanings first, then English. */
@Serializable
enum class StudyDirection(val labelKo: String, val descriptionKo: String) {
    BOTH("둘 다", "1차는 영어를 보고 한글 뜻, 2차는 뜻을 보고 영어를 써요."),
    MEANING("한글 쓰기", "영어 단어를 보고 한글 뜻을 써요."),
    ENGLISH("영어 쓰기", "한글 뜻을 보고 영어 단어를 써요."),
}

@Serializable
enum class AnswerMethod(val labelKo: String, val descriptionKo: String) {
    HANDWRITING("손글씨", "펜이나 손가락으로 써요. 읽힌 글자는 눌러서 고칠 수 있어요."),
    KEYBOARD("키보드", "자판으로 입력해요."),
    SELF("스스로 채점", "정답을 보고 알았는지 몰랐는지 골라요. 긴 뜻도 빨리 넘어가요."),
}

@Serializable
data class StudySettings(
    val source: StudySource = StudySource.NEW,
    val count: Int = 20,
    val direction: StudyDirection = StudyDirection.BOTH,
    val method: AnswerMethod = AnswerMethod.HANDWRITING,
) {
    companion object {
        val COUNTS = listOf(10, 20, 50, 100)
    }
}

/** One question: show the English and ask for the meaning, or the other way round. */
data class QuizQuestion(val wordId: String, val askEnglish: Boolean)

/**
 * The order of a word test, apart from the screen so it can be unit tested.
 * A wrong answer sends the question to the back of the line, so every word is answered
 * correctly before the test ends. A word missed even once counts as failed.
 */
class WordQuiz(private val wordIds: List<String>, val direction: StudyDirection) {
    var round: Int = 1
        private set
    private val queue = ArrayDeque<QuizQuestion>()
    var current: QuizQuestion? = null
        private set
    private val failed = linkedSetOf<String>()

    /** A wrong answer counts only once the learner moves on, so it can still be overridden. */
    private var pendingWrong = false

    var wrongAnswers: Int = 0
        private set

    /** Questions in this round still to be answered, the current one included. */
    val remaining: Int get() = queue.size + (if (current != null) 1 else 0)

    val roundSize: Int get() = wordIds.size
    val rounds: Int get() = if (direction == StudyDirection.BOTH) 2 else 1
    val failedIds: Set<String> get() = failed
    val finished: Boolean get() = current == null && queue.isEmpty()

    init {
        fillRound()
        current = queue.removeFirstOrNull()
    }

    private fun fillRound() {
        val askEnglish = when (direction) {
            StudyDirection.BOTH -> round == 2
            StudyDirection.MEANING -> false
            StudyDirection.ENGLISH -> true
        }
        wordIds.forEach { queue.addLast(QuizQuestion(it, askEnglish)) }
    }

    fun answer(correct: Boolean) {
        val question = current ?: return
        if (!correct) {
            wrongAnswers++
            pendingWrong = true
            queue.addLast(question)
        }
    }

    /** "I wrote it right": undoes the last wrong answer, for synonyms the grader did not know. */
    fun overrideLastWrong() {
        if (current == null || !pendingWrong) return
        pendingWrong = false
        wrongAnswers--
        queue.removeLastOrNull()
    }

    /** Moves on. Returns true when a new round just started. */
    fun next(): Boolean {
        if (pendingWrong) current?.let { failed += it.wordId }
        pendingWrong = false
        current = queue.removeFirstOrNull()
        if (current == null && round < rounds) {
            round++
            fillRound()
            current = queue.removeFirstOrNull()
            return true
        }
        return false
    }
}
