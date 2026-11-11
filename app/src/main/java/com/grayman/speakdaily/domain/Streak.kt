package com.grayman.speakdaily.domain

object Streak {
    /**
     * Counts consecutive practiced days ending today. A streak ending yesterday
     * still counts, because today's practice may simply not have happened yet.
     */
    fun current(practicedEpochDays: Collection<Long>, today: Long): Int {
        val days = practicedEpochDays.toHashSet()
        var day = if (today in days) today else today - 1
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }
}
