package com.grayman.englishstudy.domain

/** Simplified SM-2 spaced repetition. Pure logic: the caller supplies today's epoch day. */
object Sm2Scheduler {

    data class State(
        val intervalDays: Int = 0,
        val ease: Double = START_EASE,
        val dueEpochDay: Long = 0,
    )

    enum class Grade { AGAIN, HARD, GOOD, EASY }

    const val START_EASE = 2.5
    const val MIN_EASE = 1.3

    fun review(state: State, grade: Grade, todayEpochDay: Long): State {
        val ease = when (grade) {
            Grade.AGAIN -> state.ease - 0.2
            Grade.HARD -> state.ease - 0.15
            Grade.GOOD -> state.ease
            Grade.EASY -> state.ease + 0.15
        }.coerceAtLeast(MIN_EASE)

        val interval = when {
            grade == Grade.AGAIN -> 1
            state.intervalDays == 0 -> if (grade == Grade.EASY) 3 else 1
            state.intervalDays == 1 -> if (grade == Grade.HARD) 2 else 3
            grade == Grade.HARD -> (state.intervalDays * 1.2).toInt().coerceAtLeast(state.intervalDays + 1)
            grade == Grade.EASY -> (state.intervalDays * ease * 1.3).toInt()
            else -> (state.intervalDays * ease).toInt()
        }
        return State(interval, ease, todayEpochDay + interval)
    }

    /** Maps a spoken-match percentage to a grade, so reviewing never needs self-rating. */
    fun gradeFor(matchPercent: Int): Grade = when {
        matchPercent >= 95 -> Grade.EASY
        matchPercent >= 75 -> Grade.GOOD
        matchPercent >= 50 -> Grade.HARD
        else -> Grade.AGAIN
    }
}
