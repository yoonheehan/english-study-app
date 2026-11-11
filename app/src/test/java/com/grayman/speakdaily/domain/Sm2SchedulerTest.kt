package com.grayman.speakdaily.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {
    private val today = 1000L

    @Test
    fun `new card reviewed well comes back tomorrow`() {
        val next = Sm2Scheduler.review(Sm2Scheduler.State(), Sm2Scheduler.Grade.GOOD, today)
        assertEquals(1, next.intervalDays)
        assertEquals(today + 1, next.dueEpochDay)
    }

    @Test
    fun `good reviews grow the interval`() {
        var state = Sm2Scheduler.State()
        val intervals = (1..4).map {
            state = Sm2Scheduler.review(state, Sm2Scheduler.Grade.GOOD, today)
            state.intervalDays
        }
        assertEquals(listOf(1, 3, 7, 17), intervals)
    }

    @Test
    fun `failing resets the interval and lowers ease`() {
        val mature = Sm2Scheduler.State(intervalDays = 30, ease = 2.5)
        val next = Sm2Scheduler.review(mature, Sm2Scheduler.Grade.AGAIN, today)
        assertEquals(1, next.intervalDays)
        assertTrue(next.ease < 2.5)
    }

    @Test
    fun `ease never drops below the floor`() {
        var state = Sm2Scheduler.State()
        repeat(30) { state = Sm2Scheduler.review(state, Sm2Scheduler.Grade.AGAIN, today) }
        assertEquals(Sm2Scheduler.MIN_EASE, state.ease, 0.0001)
    }

    @Test
    fun `hard always moves a mature card forward`() {
        val next = Sm2Scheduler.review(Sm2Scheduler.State(intervalDays = 3, ease = 1.3), Sm2Scheduler.Grade.HARD, today)
        assertTrue(next.intervalDays > 3)
    }

    @Test
    fun `match percent maps to grades at boundaries`() {
        assertEquals(Sm2Scheduler.Grade.EASY, Sm2Scheduler.gradeFor(95))
        assertEquals(Sm2Scheduler.Grade.GOOD, Sm2Scheduler.gradeFor(94))
        assertEquals(Sm2Scheduler.Grade.GOOD, Sm2Scheduler.gradeFor(75))
        assertEquals(Sm2Scheduler.Grade.HARD, Sm2Scheduler.gradeFor(50))
        assertEquals(Sm2Scheduler.Grade.AGAIN, Sm2Scheduler.gradeFor(49))
    }
}
