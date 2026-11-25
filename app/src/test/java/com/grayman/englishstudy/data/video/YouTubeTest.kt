package com.grayman.englishstudy.data.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {
    @Test fun videoIdFromEveryKindOfLink() {
        val id = "arj7oStGLkU"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=30s",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "Check this out! https://youtu.be/$id",
            id,
        ).forEach { assertEquals(it, id, YouTubeLinks.videoId(it)) }
        assertNull(YouTubeLinks.videoId("https://example.com/watch?v=short"))
        assertNull(YouTubeLinks.videoId("hello world"))
    }

    @Test fun captionsAreParsedAndUnescaped() {
        val xml = """<?xml version="1.0" encoding="utf-8" ?><transcript>
            <text start="1.57" dur="7.39">[Music]</text>
            <text start="7.52" dur="4.36">I&amp;#39;m honored to be
with you today</text>
            <text start="12" dur="3">Tom &amp;amp; Jerry &lt;b&gt;</text>
            <text start="15.5">no duration</text>
        </transcript>"""
        val cues = CaptionParser.withoutSoundLabels(CaptionParser.parse(xml))
        assertEquals(3, cues.size)
        assertEquals(Cue(7520, 4360, "I'm honored to be with you today"), cues[0])
        assertEquals("Tom & Jerry", cues[1].text)
        assertEquals(2000, cues[2].durMs)
    }

    @Test fun koreanLinesFollowTheEnglishTiming() {
        val en = listOf(Cue(0, 2000, "Hello."), Cue(2000, 2000, "How are you?"), Cue(9000, 1000, "Bye."))
        val ko = listOf(Cue(100, 1800, "안녕하세요."), Cue(2100, 2500, "잘 지내요?"))
        assertEquals(listOf("안녕하세요.", "잘 지내요?", ""), CaptionParser.align(en, ko))
        assertEquals(emptyList<String>(), CaptionParser.align(en, emptyList()))
    }

    @Test fun searchResultsAndNextPageAreRead() {
        val response = """
            {"contents":{"sectionListRenderer":{"contents":[
              {"itemSectionRenderer":{"contents":[
                {"videoRenderer":{"videoId":"arj7oStGLkU","title":{"runs":[{"text":"Inside the Mind of a Master Procrastinator"}]},
                  "ownerText":{"runs":[{"text":"TED"}]},"lengthText":{"simpleText":"14:04"},"shortViewCountText":{"simpleText":"62M views"}}},
                {"videoRenderer":{"videoId":"liveStream01","title":{"runs":[{"text":"Live now"}]}}},
                {"videoRenderer":{"videoId":"bad","title":{"runs":[{"text":"broken id"}]},"lengthText":{"simpleText":"1:00"}}}
              ]}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT_TOKEN"}}}}
            ]}}}
        """.trimIndent()
        val page = YouTubeSearch.parse(kotlinx.serialization.json.Json.parseToJsonElement(response))
        assertEquals(listOf("arj7oStGLkU"), page.items.map { it.videoId })
        assertEquals("TED", page.items.first().channel)
        assertEquals("14:04", page.items.first().length)
        assertEquals("NEXT_TOKEN", page.next)
        assertEquals("https://i.ytimg.com/vi/arj7oStGLkU/mqdefault.jpg", page.items.first().thumbnail)
    }

    @Test fun currentLineIsFoundByTime() {
        val c = VideoCaptions("x", "t", "a", 10, false, listOf(Cue(1000, 1000, "a"), Cue(2000, 1000, "b"), Cue(5000, 1000, "c")))
        assertEquals(-1, c.indexAt(500))
        assertEquals(0, c.indexAt(1500))
        assertEquals(1, c.indexAt(4999))
        assertEquals(2, c.indexAt(99_000))
    }
}
