package com.grayman.englishstudy.data.video

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** One search result. */
data class VideoItem(
    val videoId: String,
    val title: String,
    val channel: String,
    val length: String,
    val views: String,
) {
    /** 320x180, small enough for a grid of cards. */
    val thumbnail: String get() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
}

/** Results so far for one topic, and the token for the next ones (null at the end). */
data class SearchPage(val items: List<VideoItem>, val next: String?)

/** A topic: what the learner sees and what is searched. */
data class VideoTopic(val titleKo: String, val query: String)

/**
 * Lists videos for a topic the way YouTube's search does, most viewed first and only videos with
 * subtitles, through the same endpoint the YouTube website uses (no API key). It is unofficial,
 * so it can stop working if YouTube changes it; the screen then says so.
 */
class YouTubeSearch {
    private val json = Json { ignoreUnknownKeys = true }

    private val cache = mutableMapOf<String, SearchPage>()
    private val lock = kotlinx.coroutines.sync.Mutex()

    /** Results already loaded for [query], kept while the app runs so paging back is instant. */
    fun cached(query: String): SearchPage? = cache[query]

    /** Loads results for [query] until there are at least [count] of them, or no more. */
    suspend fun atLeast(query: String, count: Int): SearchPage = lock.withLock {
        var page = cache[query] ?: search(query)
        while (page.items.size < count && page.next != null) {
            val more = more(page.next!!)
            page = SearchPage((page.items + more.items).distinctBy { it.videoId }, more.next.takeIf { more.items.isNotEmpty() })
        }
        cache[query] = page
        page
    }

    suspend fun search(query: String): SearchPage = post(
        """{"context":$CONTEXT,"query":${JsonPrimitive(query)},"params":"$MOST_VIEWED_WITH_SUBTITLES"}""",
    )

    suspend fun more(token: String): SearchPage = post("""{"context":$CONTEXT,"continuation":${JsonPrimitive(token)}}""")

    private suspend fun post(body: String): SearchPage = withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        parse(json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }))
    }

    companion object {
        private const val TIMEOUT_MS = 20_000
        private const val CONTEXT = """{"client":{"clientName":"WEB","clientVersion":"2.20250312.04.00","hl":"en","gl":"US"}}"""

        /** Search filter: sort by view count, only videos with subtitles/CC. */
        private const val MOST_VIEWED_WITH_SUBTITLES = "CAMSAigB"

        val TOPICS = listOf(
            VideoTopic("인기 TED 강연", "TED talk"),
            VideoTopic("TED-Ed 애니메이션", "TED-Ed"),
            VideoTopic("영어 공부 채널", "BBC Learning English"),
            VideoTopic("일상 영어 회화", "English conversation"),
            VideoTopic("비즈니스 영어", "business English"),
            VideoTopic("과학·지식", "Kurzgesagt"),
            VideoTopic("명연설", "commencement speech"),
            VideoTopic("뉴스로 듣기", "CNN 10"),
            VideoTopic("여행", "travel vlog"),
            VideoTopic("요리", "cooking recipe"),
        )

        /** Reads the videos and the next-page token out of a search response. Pure, so it is unit tested. */
        fun parse(root: JsonElement): SearchPage {
            val items = mutableListOf<VideoItem>()
            var next: String? = null
            fun text(e: JsonElement?): String {
                val o = e as? JsonObject ?: return ""
                (o["simpleText"] as? JsonPrimitive)?.let { return it.content }
                return (o["runs"] as? JsonArray)?.joinToString("") { r -> ((r as? JsonObject)?.get("text") as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
            }
            fun walk(e: JsonElement) {
                when (e) {
                    is JsonObject -> {
                        (e["videoRenderer"] as? JsonObject)?.let { v ->
                            val id = (v["videoId"] as? JsonPrimitive)?.content
                            if (id != null && YouTubeLinks.videoId(id) == id && items.none { it.videoId == id }) {
                                items += VideoItem(id, text(v["title"]), text(v["ownerText"]), text(v["lengthText"]), text(v["shortViewCountText"]).ifEmpty { text(v["viewCountText"]) })
                            }
                            return
                        }
                        (e["continuationCommand"] as? JsonObject)?.get("token")?.let { if (next == null) next = runCatching { it.jsonPrimitive.content }.getOrNull() }
                        e.values.forEach(::walk)
                    }
                    is JsonArray -> e.forEach(::walk)
                    else -> Unit
                }
            }
            walk(root)
            // Live streams have no length; they cannot be studied line by line.
            return SearchPage(items.filter { it.length.isNotEmpty() }, next)
        }
    }
}
