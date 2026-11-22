package com.grayman.englishstudy.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The real content files must survive the trip to Firestore documents and back unchanged. */
class ContentCodecTest {
    private fun file(level: Level, name: String) = File("src/main/assets/content/${level.code}/$name").readText()

    /** Firestore hands documents back in id order, not file order. */
    private fun throughFirestore(array: JsonArray): JsonArray =
        ContentCodec.array(ContentCodec.documents(array).sortedBy { it["id"].toString() })

    @Test
    fun lessonsRoundTrip() = Level.entries.forEach { level ->
        val original = Json.parseToJsonElement(file(level, ContentSync.LESSONS_FILE)).jsonArray
        val back = throughFirestore(original)
        assertEquals(level.code, original, back)
        assertEquals(level.code, ContentParser.parseLessons(file(level, ContentSync.LESSONS_FILE)), ContentParser.parseLessons(back.toString()))
    }

    @Test
    fun listeningRoundTrip() = Level.entries.forEach { level ->
        val original = Json.parseToJsonElement(file(level, ContentSync.LISTENING_FILE)).jsonObject
        val back = JsonObject(
            mapOf(
                "qr" to throughFirestore(original.getValue("qr").jsonArray),
                "sets" to throughFirestore(original.getValue("sets").jsonArray),
            ),
        )
        assertEquals(ListeningParser.parse(original.toString()), ListeningParser.parse(back.toString()))
    }

    /** Each item becomes a document named by its id, so ids must be unique within a collection. */
    @Test
    fun idsAreUniqueDocumentNames() = Level.entries.forEach { level ->
        val lessons = Json.parseToJsonElement(file(level, ContentSync.LESSONS_FILE)).jsonArray
        val listening = Json.parseToJsonElement(file(level, ContentSync.LISTENING_FILE)).jsonObject
        listOf(lessons, listening.getValue("qr").jsonArray, listening.getValue("sets").jsonArray).forEach { items ->
            val ids = items.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertEquals("${level.code} duplicate ids", ids.size, ids.toSet().size)
            ids.forEach { assert('/' !in it && it.isNotBlank()) { "bad document id: $it" } }
        }
    }
}
