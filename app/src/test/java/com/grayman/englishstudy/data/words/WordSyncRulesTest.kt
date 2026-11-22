package com.grayman.englishstudy.data.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSyncRulesTest {
    private val word = WordEntity(
        id = "abc",
        word = "resilient",
        partOfSpeech = "adjective",
        meaning = "회복력 있는",
        pronunciation = "/rɪˈzɪliənt/",
        example1 = "She is resilient.",
        example1Meaning = "그녀는 회복력이 강하다.",
        example2 = "",
        example2Meaning = "",
        memo = "memo",
        sourceSentence = "A resilient team.",
        sourceUrl = "https://example.com",
        favorite = true,
        mastery = 100,
        masteredAt = 1_700_000_000,
        reviewStage = 2,
        nextReviewAt = 1_700_100_000,
        wrongCount = 3,
        createdAt = 1_000,
        updatedAt = 2_000,
        deleted = false,
        pendingSync = true,
    )

    @Test
    fun roundTripKeepsEverySyncedFieldAndDropsTheLocalFlag() {
        // Firestore hands numbers back as Long, which is what toMap already writes.
        val back = WordSyncRules.fromMap(word.id, WordSyncRules.toMap(word))
        assertEquals(word.copy(pendingSync = false), back)
        assertFalse(WordSyncRules.toMap(word).containsKey("pendingSync"))
    }

    @Test
    fun missingTextOrTimestampIsSkipped() {
        val map = WordSyncRules.toMap(word)
        assertNull(WordSyncRules.fromMap("abc", map - "word"))
        assertNull(WordSyncRules.fromMap("abc", map - "updatedAt"))
    }

    private val remote = word.copy(pendingSync = false)

    @Test
    fun newCardIsStored() {
        assertEquals(remote, WordSyncRules.merge(null, remote))
    }

    @Test
    fun ownUploadComingBackChangesNothing() {
        assertNull(WordSyncRules.merge(remote, remote))
    }

    @Test
    fun newerRemoteProgressWins() {
        val local = remote.copy(favorite = false, updatedAt = 1_000)
        assertEquals(remote, WordSyncRules.merge(local, remote))
    }

    @Test
    fun unsentLocalProgressIsKeptButTextComesFromThePc() {
        // The PC fixed the meaning at 2,000; this device starred the card at 3,000 and has not uploaded yet.
        val local = remote.copy(meaning = "옛 뜻", favorite = false, updatedAt = 3_000, pendingSync = true)
        val merged = WordSyncRules.merge(local, remote)!!
        assertEquals("회복력 있는", merged.meaning)
        assertFalse(merged.favorite)
        assertEquals(3_000, merged.updatedAt)
        assertTrue(merged.pendingSync)
    }

    @Test
    fun deviceUploadsOnlyProgressFields() {
        val keys = WordSyncRules.progressMap(word).keys
        assertEquals(WordSyncRules.PROGRESS_FIELDS.toSet(), keys)
        assertFalse("word" in keys || "meaning" in keys || "deleted" in keys)
    }
}
