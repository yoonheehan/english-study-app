package com.grayman.speakdaily.domain

import com.grayman.speakdaily.data.ListeningPool
import com.grayman.speakdaily.data.ListeningSet
import com.grayman.speakdaily.data.QuestionResponseItem
import com.grayman.speakdaily.data.SetType
import kotlin.random.Random

enum class TestPart(val labelKo: String, val guideKo: String) {
    QUESTION_RESPONSE("질문-응답", "질문을 듣고 가장 알맞은 대답을 고르세요. 보기도 소리로만 나와요."),
    CONVERSATION("짧은 대화", "두 사람의 대화를 듣고 문제 3개를 푸세요."),
    TALK("안내방송·담화", "한 사람의 말을 듣고 문제 3개를 푸세요."),
}

/** A screen of the test: one response question, or one recording with its questions. */
sealed interface TestPage {
    val part: TestPart
    val questionCount: Int

    data class Response(val item: QuestionResponseItem) : TestPage {
        override val part = TestPart.QUESTION_RESPONSE
        override val questionCount = 1
    }

    data class Set(val set: ListeningSet) : TestPage {
        override val part = if (set.type == SetType.TALK) TestPart.TALK else TestPart.CONVERSATION
        override val questionCount = set.questions.size
    }
}

enum class TestLength(val questions: Int, val responses: Int, val conversations: Int, val talks: Int) {
    SHORT(10, 4, 1, 1),
    FULL(20, 8, 2, 2),
}

object ListeningTestBuilder {
    /**
     * Picks a test in exam order (all response questions first, then conversations, then talks).
     * Items seen least recently come first, so repeating a level does not replay the same audio.
     */
    fun build(
        pool: ListeningPool,
        length: TestLength,
        recentlySeen: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): List<TestPage> {
        fun <T> pick(items: List<T>, count: Int, id: (T) -> String): List<T> {
            val (fresh, seen) = items.shuffled(random).partition { id(it) !in recentlySeen }
            return (fresh + seen).take(count)
        }
        val responses = pick(pool.qr, length.responses) { it.id }.map { TestPage.Response(it) }
        val conversations = pick(pool.sets.filter { it.type == SetType.CONVERSATION }, length.conversations) { it.id }
        val talks = pick(pool.sets.filter { it.type == SetType.TALK }, length.talks) { it.id }
        return responses + conversations.map { TestPage.Set(it) } + talks.map { TestPage.Set(it) }
    }

    fun pageIds(pages: List<TestPage>): List<String> = pages.map {
        when (it) {
            is TestPage.Response -> it.item.id
            is TestPage.Set -> it.set.id
        }
    }
}

/** Results per part and per accent, so the learner sees which kind of listening is weak. */
object ListeningScorer {
    data class Tally(val correct: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else correct * 100 / total
    }

    data class Report(
        val total: Tally,
        val byPart: Map<TestPart, Tally>,
        val byAccent: Map<String, Tally>,
    )

    /** One answered question: which part, which accent it was spoken in, and whether it was right. */
    data class Answered(val part: TestPart, val accent: String, val correct: Boolean)

    fun report(answers: List<Answered>): Report {
        fun tally(list: List<Answered>) = Tally(list.count { it.correct }, list.size)
        return Report(
            total = tally(answers),
            byPart = answers.groupBy { it.part }.mapValues { tally(it.value) },
            byAccent = answers.groupBy { it.accent }.mapValues { tally(it.value) },
        )
    }

    enum class Advice { MOVE_UP, STAY, MOVE_DOWN }

    /** The test suggests a level change but never changes it on its own. */
    fun advice(total: Tally): Advice = when {
        total.percent >= 80 -> Advice.MOVE_UP
        total.percent < 50 -> Advice.MOVE_DOWN
        else -> Advice.STAY
    }
}
