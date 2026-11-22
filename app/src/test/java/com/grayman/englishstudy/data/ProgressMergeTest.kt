package com.grayman.englishstudy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMergeTest {
    private val empty = ProgressSnapshot(null, emptyList(), emptyList(), emptyList(), emptyList())

    private val phone = ProgressSnapshot(
        profile = ProfileEntity(levelCode = "a1", totalXp = 60),
        lessons = listOf(LessonProgressEntity("a1-01", 1, 80, 100), LessonProgressEntity("a1-02", 1, 70, 100)),
        days = listOf(DailyRecordEntity(100, 60, 120, 2)),
        cards = listOf(ReviewCardEntity("hello", "안녕", 1, 2.5, 101)),
        listening = listOf(ListeningResultEntity(id = 7, epochDay = 100, levelCode = "a1", correct = 8, total = 10, itemIds = "x,y")),
    )

    private val tablet = ProgressSnapshot(
        profile = ProfileEntity(levelCode = "b1", totalXp = 40),
        lessons = listOf(LessonProgressEntity("a1-01", 2, 60, 101)),
        days = listOf(DailyRecordEntity(101, 40, 60, 1)),
        cards = listOf(ReviewCardEntity("hello", "안녕", 6, 2.6, 107), ReviewCardEntity("bye", "잘 가", 0, 2.5, 102)),
        listening = listOf(ListeningResultEntity(id = 3, epochDay = 100, levelCode = "a1", correct = 8, total = 10, itemIds = "x,y")),
    )

    @Test fun recordsFromBothDevicesAreCombined() {
        val m = ProgressMerge.merge(phone, tablet)
        assertEquals(LessonProgressEntity("a1-01", 2, 80, 101), m.lessons.first { it.lessonId == "a1-01" })
        assertEquals(2, m.lessons.size)
        assertEquals(setOf(100L, 101L), m.days.map { it.epochDay }.toSet())
        assertEquals(107L, m.cards.first { it.en == "hello" }.dueEpochDay)
        assertEquals(2, m.cards.size)
        assertEquals(1, m.listening.size) // the same test seen on both sides
        assertEquals(100, m.profile!!.totalXp) // 60 + 40 from the two days
        assertEquals("a1", m.profile!!.levelCode) // this device keeps its own level
    }

    @Test fun mergingAgainChangesNothing() {
        val once = ProgressMerge.merge(phone, tablet)
        val twice = ProgressMerge.merge(once, tablet)
        assertEquals(once.lessons.toSet(), twice.lessons.toSet())
        assertEquals(once.days.toSet(), twice.days.toSet())
        assertEquals(once.cards.toSet(), twice.cards.toSet())
        assertEquals(once.listening.size, twice.listening.size)
        assertEquals(once.profile, twice.profile)
    }

    @Test fun newDeviceTakesTheOtherDevicesRecords() {
        val m = ProgressMerge.merge(empty, phone)
        assertEquals("a1", m.profile!!.levelCode)
        assertEquals(60, m.profile!!.totalXp)
        assertEquals(2, m.lessons.size)
        assertNull(ProgressMerge.merge(empty, empty).profile)
    }

    @Test fun activityHistoryFromBothDevicesIsKeptOnce() {
        val a = ActivityEntity("id-1", 1000, 100, "LESSON", "인사", "점수 80", 30)
        val b = ActivityEntity("id-2", 2000, 101, "WORDS", "단어 외우기", "20개", 50)
        val m = ProgressMerge.merge(phone.copy(activities = listOf(a)), tablet.copy(activities = listOf(a, b)))
        assertEquals(listOf("id-1", "id-2"), m.activities.map { it.id })
        val back = ProgressMerge.fromMaps(ProgressMerge.toMaps(m))
        assertEquals(m.activities, back.activities)
    }

    @Test fun firestoreFormatRoundTrips() {
        val back = ProgressMerge.fromMaps(ProgressMerge.toMaps(phone))
        assertEquals(phone.profile, back.profile)
        assertEquals(phone.lessons, back.lessons)
        assertEquals(phone.days, back.days)
        assertEquals(phone.cards, back.cards)
        assertEquals(phone.listening.map { it.copy(id = 0) }, back.listening)
    }
}
