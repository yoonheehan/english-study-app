package com.grayman.englishstudy.data.words

import com.grayman.englishstudy.data.StoryText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAddingTest {
    @Test fun duplicatesIgnoreCaseSpacesAndBlankPartOfSpeech() {
        assertTrue(WordDuplicates.sameCard("Available", "adjective", "available ", "adjective"))
        assertTrue(WordDuplicates.sameCard("look  up", "", "Look up", "phrase"))
        assertFalse(WordDuplicates.sameCard("record", "noun", "record", "verb"))
        assertFalse(WordDuplicates.sameCard("record", "noun", "records", "noun"))
    }

    @Test fun duplicateGroupsKeepDifferentPartsOfSpeechApart() {
        val cards = listOf("record" to "noun", "Record" to "noun", "record" to "verb", "apple" to "", "apple" to "noun", "pear" to "noun")
        val groups = WordDuplicates.groups(cards) { it }
        assertEquals(2, groups.size)
        assertTrue(groups.any { g -> g.size == 2 && g.all { it.first.lowercase() == "record" && it.second == "noun" } })
        assertTrue(groups.any { g -> g.map { it.first }.toSet() == setOf("apple") })
    }

    @Test fun aiReplyIsParsedEvenWithMarkdown() {
        val fill = WordMaker.parse(
            """
            **LEMMA:** run
            POS: Verb
            IPA: rʌn
            MEANING: 달리다, 운영하다
            SENTENCE_KO: 그는 역으로 달려갔다.
            EXAMPLE: I run every morning.
            EXAMPLE_KO: 나는 매일 아침 달린다.
            """.trimIndent(),
        )
        assertNotNull(fill)
        fill!!
        assertEquals("run", fill.lemma)
        assertEquals("verb", fill.partOfSpeech)
        assertEquals("/rʌn/", fill.pronunciation)
        assertEquals("달리다, 운영하다", fill.meaning)
        assertNull(WordMaker.parse("Sorry, I can't help with that."))
    }

    @Test fun cardKeepsTheSentenceAsFirstExample() {
        val ai = WordMaker.AiFill("run", "verb", "/rʌn/", "달리다", "그는 역으로 달려갔다.", "I run every morning.", "나는 매일 아침 달린다.")
        val f = WordMaker.fields("ran", "He ran to the station.", "달렸다", "그는 역까지 달렸다.", ai)
        assertEquals("run", f.word)
        assertEquals("He ran to the station.", f.example1)
        assertEquals("그는 역으로 달려갔다.", f.example1Meaning)
        assertEquals("I run every morning.", f.example2)
        // Without the AI the translator's meaning is used and nothing is invented.
        val quick = WordMaker.fields("ran", "He ran to the station.", "달렸다", "그는 역까지 달렸다.", null)
        assertEquals("ran", quick.word)
        assertEquals("달렸다", quick.meaning)
        assertEquals("", quick.example2)
        // A lemma that is clearly another word is ignored.
        assertEquals("ran", WordMaker.fields("ran", "", "x", "", ai.copy(lemma = "sprint")).word)
        // What the learner typed wins.
        assertEquals("뛰다", WordMaker.fields("ran", "", "달렸다", "", ai, editedMeaning = "뛰다").meaning)
    }

    @Test fun sentenceAroundAWordSkipsTitles() {
        val text = "It was late. Mr. Holmes looked up from his chair. “Come in!” he said."
        assertEquals("Mr. Holmes looked up from his chair.", WordMaker.sentenceAround(text, text.indexOf("chair")))
        assertEquals("It was late.", WordMaker.sentenceAround(text, 3))
    }

    @Test fun tappedWordIsFoundWithoutQuotesOrPossessives() {
        val text = "“Why can’t you be like the Happy Prince?” asked a well-known mother."
        val r = StoryText.wordAt(text, text.indexOf("can’t") + 2)!!
        assertEquals("can’t", text.substring(r))
        val q = StoryText.wordAt(text, 1)!!
        assertEquals("Why", text.substring(q))
        assertEquals("well-known", text.substring(StoryText.wordAt(text, text.indexOf("known"))!!))
        assertNull(StoryText.wordAt(text, text.indexOf("?")+1))
        assertEquals("Prince", StoryText.lookupForm("Prince’s"))
    }

    @Test fun priorityTravelsWithTheCard() {
        val card = WordEntity.create(WordFields("glimpse", meaning = "흘끗 봄"), 1L).copy(priority = true)
        val back = WordSyncRules.fromMap(card.id, WordSyncRules.toMap(card))!!
        assertTrue(back.priority)
        assertTrue("priority" in WordSyncRules.PROGRESS_FIELDS)
    }

    /** The app's progress fields and the rules file must agree, or uploads are refused. */
    @Test fun firestoreRulesAllowWhatTheAppSends() {
        // The real rules file holds personal data and stays out of git; a fresh clone has only the template.
        val rules = (File("../firestore.rules").takeIf { it.isFile } ?: File("../firestore.rules.example")).readText()
        WordSyncRules.PROGRESS_FIELDS.forEach { assertTrue("rules miss $it", rules.contains("'$it'")) }
        assertTrue(rules.contains("allow create: if isMyDevice()"))
        assertFalse("devices must not delete", Regex("allow [a-z, ]*delete[a-z, ]*: if isMyDevice").containsMatchIn(rules))
    }
}
