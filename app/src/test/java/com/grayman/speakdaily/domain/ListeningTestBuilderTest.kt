package com.grayman.speakdaily.domain

import com.grayman.speakdaily.data.Line
import com.grayman.speakdaily.data.ListeningPool
import com.grayman.speakdaily.data.ListeningSet
import com.grayman.speakdaily.data.QuestionResponseItem
import com.grayman.speakdaily.data.SetQuestion
import com.grayman.speakdaily.data.SetType
import com.grayman.speakdaily.data.SpokenOption
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningTestBuilderTest {
    private fun qr(id: String) = QuestionResponseItem(id, "q", "", List(3) { SpokenOption("o", "") }, 0)

    private fun set(id: String, type: SetType) =
        ListeningSet(id, type, "", listOf(Line(0, "x", "")), List(3) { SetQuestion("q", "", List(4) { "o" }, 0) })

    private val pool = ListeningPool(
        qr = (1..10).map { qr("qr$it") },
        sets = (1..3).map { set("c$it", SetType.CONVERSATION) } + (1..3).map { set("t$it", SetType.TALK) },
    )

    @Test
    fun `short test has ten questions in exam order`() {
        val pages = ListeningTestBuilder.build(pool, TestLength.SHORT, random = Random(1))
        assertEquals(10, pages.sumOf { it.questionCount })
        assertEquals(
            listOf(TestPart.QUESTION_RESPONSE, TestPart.CONVERSATION, TestPart.TALK),
            pages.map { it.part }.distinct(),
        )
    }

    @Test
    fun `full test has twenty questions`() {
        val pages = ListeningTestBuilder.build(pool, TestLength.FULL, random = Random(2))
        assertEquals(20, pages.sumOf { it.questionCount })
    }

    @Test
    fun `no item appears twice`() {
        val ids = ListeningTestBuilder.pageIds(ListeningTestBuilder.build(pool, TestLength.FULL, random = Random(3)))
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `recently seen items are used only when fresh ones run out`() {
        val seen = (1..6).map { "qr$it" }.toSet() + "c1" + "t1"
        val ids = ListeningTestBuilder.pageIds(ListeningTestBuilder.build(pool, TestLength.SHORT, seen, Random(4)))
        assertTrue(ids.none { it in seen })
    }

    @Test
    fun `scorer splits results by part and accent`() {
        val report = ListeningScorer.report(
            listOf(
                ListeningScorer.Answered(TestPart.QUESTION_RESPONSE, "영국", true),
                ListeningScorer.Answered(TestPart.QUESTION_RESPONSE, "인도", false),
                ListeningScorer.Answered(TestPart.TALK, "영국", true),
            ),
        )
        assertEquals(ListeningScorer.Tally(2, 3), report.total)
        assertEquals(50, report.byPart.getValue(TestPart.QUESTION_RESPONSE).percent)
        assertEquals(ListeningScorer.Tally(2, 2), report.byAccent.getValue("영국"))
    }

    @Test
    fun `advice thresholds`() {
        assertEquals(ListeningScorer.Advice.MOVE_UP, ListeningScorer.advice(ListeningScorer.Tally(8, 10)))
        assertEquals(ListeningScorer.Advice.STAY, ListeningScorer.advice(ListeningScorer.Tally(5, 10)))
        assertEquals(ListeningScorer.Advice.MOVE_DOWN, ListeningScorer.advice(ListeningScorer.Tally(4, 10)))
        assertEquals(ListeningScorer.Advice.MOVE_DOWN, ListeningScorer.advice(ListeningScorer.Tally(0, 0)))
    }
}
