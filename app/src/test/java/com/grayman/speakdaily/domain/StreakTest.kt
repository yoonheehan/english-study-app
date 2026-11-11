package com.grayman.speakdaily.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    @Test
    fun `no practice means zero`() = assertEquals(0, Streak.current(emptyList(), 100))

    @Test
    fun `consecutive days ending today count`() =
        assertEquals(3, Streak.current(listOf(98, 99, 100), 100))

    @Test
    fun `streak ending yesterday is still alive`() =
        assertEquals(2, Streak.current(listOf(98, 99), 100))

    @Test
    fun `a missed day breaks the streak`() =
        assertEquals(1, Streak.current(listOf(95, 96, 97, 100), 100))

    @Test
    fun `two days ago is already broken`() =
        assertEquals(0, Streak.current(listOf(97, 98), 100))
}
