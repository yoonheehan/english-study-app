package com.grayman.englishstudy.ui.home

import com.grayman.englishstudy.data.DailyRecordEntity
import com.grayman.englishstudy.data.Dictation
import com.grayman.englishstudy.data.GistQuestion
import com.grayman.englishstudy.data.Lesson
import com.grayman.englishstudy.data.LessonProgressEntity
import com.grayman.englishstudy.data.Level
import com.grayman.englishstudy.data.LevelUnit
import com.grayman.englishstudy.data.ProfileEntity
import com.grayman.englishstudy.data.ResponseCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    private fun lesson(id: String) = Lesson(
        id, id, id, "wave", "", emptyList(),
        GistQuestion("", emptyList(), 0), Dictation(0, emptyList()), emptyList(), emptyList(),
        ResponseCard("", "", emptyList(), emptyList()),
    )

    private val units = listOf(
        LevelUnit(Level.A1, listOf(lesson("a1-1"), lesson("a1-2"))),
        LevelUnit(Level.A2, listOf(lesson("a2-1"))),
    )

    private fun build(level: String = "a1", done: List<String> = emptyList(), days: List<Long> = emptyList()) =
        buildHomeState(
            ProfileEntity(levelCode = level, totalXp = 50),
            units,
            done.map { LessonProgressEntity(it, 1, 80, 1) },
            days.map { DailyRecordEntity(it, 10, 5, 1) },
            today = 100,
        )

    private fun states(state: HomeState) = state.path.flatMap { it.nodes }.map { it.state }

    @Test
    fun `new learner has the first lesson open and the rest locked`() {
        val state = build()
        assertEquals("a1-1", state.currentLessonId)
        assertEquals(listOf(NodeState.CURRENT, NodeState.OPEN, NodeState.OPEN), states(state))
    }

    @Test
    fun `finishing a lesson opens the next one`() {
        assertEquals(
            listOf(NodeState.DONE, NodeState.CURRENT, NodeState.OPEN),
            states(build(done = listOf("a1-1"))),
        )
    }

    @Test
    fun `path continues into the next level`() {
        assertEquals("a2-1", build(done = listOf("a1-1", "a1-2")).currentLessonId)
    }

    @Test
    fun `choosing a higher level hides lower units`() {
        val state = build(level = "a2")
        assertEquals(listOf(Level.A2), state.path.map { it.level })
        assertEquals("a2-1", state.currentLessonId)
    }

    @Test
    fun `everything finished leaves no current lesson`() {
        assertNull(build(done = listOf("a1-1", "a1-2", "a2-1")).currentLessonId)
    }

    @Test
    fun `streak and today flag come from daily records`() {
        val state = build(days = listOf(98, 99, 100))
        assertEquals(3, state.streak)
        assertTrue(state.practicedToday)
        assertFalse(build(days = listOf(99)).practicedToday)
    }
}
