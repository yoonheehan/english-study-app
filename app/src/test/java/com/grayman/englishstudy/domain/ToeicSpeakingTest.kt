package com.grayman.englishstudy.domain

import com.grayman.englishstudy.ai.RolePlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToeicSpeakingTest {
    @Test fun mockTestFollowsTheOfficialFormat() {
        val test = ToeicSpeaking.build(null)
        assertEquals(11, test.size)
        assertEquals((1..11).toList(), test.map { it.number })
        assertEquals(
            listOf(ToeicPart.READ_ALOUD, ToeicPart.READ_ALOUD, ToeicPart.PICTURE, ToeicPart.PICTURE) +
                List(3) { ToeicPart.RESPOND } + List(3) { ToeicPart.INFO } + ToeicPart.OPINION,
            test.map { it.part },
        )
        assertEquals(listOf(45, 45, 30, 30, 15, 15, 30, 15, 15, 30, 60), test.map { it.speakSeconds })
        assertEquals(45, test[7].prepSeconds) // reading the schedule before question 8
    }

    @Test fun onePartOnly() {
        assertTrue(ToeicSpeaking.build(ToeicPart.RESPOND).all { it.part == ToeicPart.RESPOND })
        assertEquals(1, ToeicSpeaking.build(ToeicPart.OPINION).size)
    }

    @Test fun estimateRewardsAccurateReading() {
        val q = ToeicSpeaking.build(ToeicPart.READ_ALOUD).first()
        assertEquals(3, ToeicSpeaking.estimate(q, q.modelAnswer))
        assertEquals(0, ToeicSpeaking.estimate(q, ""))
    }

    @Test fun estimateStaysOnScale() {
        val q = ToeicSpeaking.build(ToeicPart.OPINION).first()
        val long = "I agree because first it helps people, for example my friend, and also it saves time so it is better. ".repeat(6)
        val score = ToeicSpeaking.estimate(q, long)
        assertTrue(score in 3..5)
        assertTrue(ToeicSpeaking.estimate(q, "yes") <= 1)
    }

    @Test fun aiScoreIsReadFromTheReview() {
        assertEquals(2, ToeicSpeaking.parseScore("점수: 2/3\n좋은 점: ...", 3))
        assertEquals(4, ToeicSpeaking.parseScore("점수：4 / 5", 5))
        assertEquals(3, ToeicSpeaking.parseScore("점수: 7/3", 3)) // clamped
        assertEquals(null, ToeicSpeaking.parseScore("잘했어요", 3))
    }

    @Test fun scaledScoreAndGrade() {
        assertEquals(200, ToeicSpeaking.estimateScaled(35, 35))
        assertEquals(0, ToeicSpeaking.estimateScaled(0, 35))
        assertEquals(140, ToeicSpeaking.estimateScaled(25, 35))
        assertEquals("AH", ToeicSpeaking.grade(200).first)
        assertEquals("IH", ToeicSpeaking.grade(140).first)
        assertEquals("IM1", ToeicSpeaking.grade(110).first)
        assertEquals("NL", ToeicSpeaking.grade(10).first)
    }

    @Test fun everyLevelBuildsAFullMockTest() {
        ToeicLevel.entries.forEach { level ->
            val test = ToeicSpeaking.build(null, level)
            assertEquals(11, test.size)
            assertTrue(test.all { it.level == level })
            assertEquals(listOf(45, 45, 30, 30, 15, 15, 30, 15, 15, 30, 60), test.map { it.speakSeconds })
            assertTrue("opinion needs a sample answer at $level", test.last().modelAnswer.length > 200)
        }
    }

    @Test fun everyLevelHasVariedContent() {
        ToeicLevel.entries.forEach { level ->
            assertTrue(ToeicSpeaking.poolSize(ToeicPart.READ_ALOUD, level) >= 8)
            assertTrue(ToeicSpeaking.poolSize(ToeicPart.PICTURE, level) >= 12)
            assertTrue(ToeicSpeaking.poolSize(ToeicPart.RESPOND, level) >= 6)
            assertTrue(ToeicSpeaking.poolSize(ToeicPart.INFO, level) >= 4)
            assertTrue(ToeicSpeaking.poolSize(ToeicPart.OPINION, level) >= 8)
            ToeicContent.respondSets.getValue(level).forEach { assertEquals(3, it.questions.size); assertEquals(3, it.samples.size) }
            ToeicContent.infoSets.getValue(level).forEach {
                assertEquals(3, it.questions.size); assertEquals(3, it.answers.size); assertEquals(3, it.keywords.size)
                // Keywords must appear in the model answer, or the offline estimate could never reach them.
                it.keywords.forEachIndexed { i, ks -> ks.forEach { k -> assertTrue("$k in ${it.title}", it.answers[i].lowercase().contains(k)) } }
            }
        }
    }

    @Test fun readAloudGetsLongerWithLevel() {
        fun avgWords(level: ToeicLevel) = ToeicContent.readAloud.getValue(level).map { it.split(" ").size }.average()
        assertTrue(avgWords(ToeicLevel.BASIC) < avgWords(ToeicLevel.MIDDLE))
        assertTrue(avgWords(ToeicLevel.MIDDLE) < avgWords(ToeicLevel.ADVANCED))
    }

    @Test fun everyPhotoExistsOnceAndIsCredited() {
        val photos = ToeicLevel.entries.flatMap { ToeicContent.pictures.getValue(it) }
        assertEquals("a photo is used in two levels", photos.size, photos.map { it.file }.toSet().size)
        val credits = java.io.File("src/main/assets/toeic/photos/CREDITS.json").readText()
        photos.forEach { photo ->
            assertTrue(photo.assetPath, java.io.File("src/main/assets/${photo.assetPath}").isFile)
            assertTrue(photo.keywords.size >= 6)
            assertTrue("${photo.file} credit", credits.contains("\"${photo.file}\""))
        }
    }

    @Test fun rolePlayHasManyVariedTopics() {
        assertTrue(RolePlay.topics.size >= 40)
        assertEquals(RolePlay.topics.size, RolePlay.topics.map { it.id }.toSet().size)
        assertTrue(RolePlay.topics.all { it.situations.size >= 3 })
        val first = RolePlay.topic("cafe")
        val situations = (1..30).map { RolePlay.scenario(first, kotlin.random.Random(it)).situation }.toSet()
        assertTrue(situations.size > 1)
    }
}
