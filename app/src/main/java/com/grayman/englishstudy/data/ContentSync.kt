package com.grayman.englishstudy.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Converts between the lesson JSON files and Firestore documents, so the same content can live in
 * either place. Kept free of Firebase types so tests can round-trip the real files.
 */
object ContentCodec {
    /** Field added to each document to keep the file's order; removed again when reading back. */
    const val ORDER = "order"

    fun toFirestore(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { toFirestore(it.value) }
        is JsonArray -> element.map { toFirestore(it) }
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            else -> element.doubleOrNull
        }
    }

    fun fromFirestore(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to fromFirestore(v) })
        is List<*> -> JsonArray(value.map { fromFirestore(it) })
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    /** One document per array item, each tagged with its position. */
    fun documents(array: JsonArray): List<Map<String, Any?>> = array.mapIndexed { index, item ->
        @Suppress("UNCHECKED_CAST")
        (toFirestore(item) as Map<String, Any?>) + (ORDER to index.toLong())
    }

    /** Rebuilds the array from documents, in their saved order and without the order field. */
    fun array(documents: List<Map<String, Any?>>): JsonArray = JsonArray(
        documents
            .sortedBy { (it[ORDER] as? Number)?.toLong() ?: Long.MAX_VALUE }
            .map { fromFirestore(it - ORDER) },
    )
}

/**
 * Keeps lessons and listening tests in Firestore, under content/{level}/{lessons|listeningQr|listeningSets}.
 *
 * The first device to connect uploads the bundled files once. After that Firestore is the master:
 * each start downloads the content, checks that it still parses, and saves it as files that the
 * repositories read on the next start. A document broken by a console edit never reaches the
 * screens; the last good copy stays in use.
 */
class ContentSync(private val context: Context, private val firestore: FirebaseFirestore) {
    private val json = Json { prettyPrint = false }

    suspend fun refresh(uploaded: Boolean, markUploaded: suspend () -> Unit): String = withContext(Dispatchers.IO) {
        if (!uploaded) {
            uploadBundled()
            markUploaded()
        }
        download()
    }

    private suspend fun uploadBundled() {
        Level.entries.forEach { level ->
            val lessons = Json.parseToJsonElement(asset(level, LESSONS_FILE)).jsonArray
            val listening = Json.parseToJsonElement(asset(level, LISTENING_FILE)).jsonObject
            write(level, LESSONS, lessons, "id")
            write(level, LISTENING_QR, listening.getValue("qr").jsonArray, "id")
            write(level, LISTENING_SETS, listening.getValue("sets").jsonArray, "id")
        }
        Log.i(TAG, "uploaded bundled content")
    }

    private suspend fun write(level: Level, collection: String, items: JsonArray, idField: String) {
        val batch = firestore.batch()
        val levelDoc = firestore.collection(ROOT).document(level.code)
        batch.set(levelDoc, mapOf("labelKo" to level.labelKo), SetOptions.merge())
        ContentCodec.documents(items).forEach { doc ->
            batch.set(levelDoc.collection(collection).document(doc.getValue(idField).toString()), doc)
        }
        batch.commit().await()
    }

    private suspend fun download(): String {
        var lessonCount = 0
        var listeningCount = 0
        Level.entries.forEach { level ->
            val lessonDocs = fetch(level, LESSONS)
            if (lessonDocs.isEmpty()) {
                // A level added to the app before its content was uploaded: keep the bundled copy.
                Log.i(TAG, "${level.code}: not on the server yet, using the bundled content")
                return@forEach
            }
            val lessons = ContentCodec.array(lessonDocs)
            val listening = JsonObject(
                mapOf("qr" to ContentCodec.array(fetch(level, LISTENING_QR)), "sets" to ContentCodec.array(fetch(level, LISTENING_SETS))),
            )
            val lessonsText = json.encodeToString(JsonElement.serializer(), lessons)
            val listeningText = json.encodeToString(JsonElement.serializer(), listening)
            // Parse before saving, so a bad edit in the console cannot break the app.
            val parsedLessons = ContentParser.parseLessons(lessonsText)
            val parsedListening = ListeningParser.parse(listeningText)
            require(parsedLessons.isNotEmpty()) { "${level.code} 레슨이 비어 있어요" }
            save(level, LESSONS_FILE, lessonsText)
            save(level, LISTENING_FILE, listeningText)
            lessonCount += parsedLessons.size
            listeningCount += parsedListening.qr.size + parsedListening.sets.size
        }
        return "레슨 ${lessonCount}개, 리스닝 ${listeningCount}개"
    }

    private suspend fun fetch(level: Level, collection: String): List<Map<String, Any?>> =
        firestore.collection(ROOT).document(level.code).collection(collection).get().await().documents.map { it.data.orEmpty() }

    private fun asset(level: Level, file: String) =
        context.assets.open("content/${level.code}/$file").bufferedReader().use { it.readText() }

    private fun save(level: Level, file: String, text: String) {
        val target = downloaded(context, level, file)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$file.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    companion object {
        private const val TAG = "ContentSync"
        const val ROOT = "content"
        private const val LESSONS = "lessons"
        private const val LISTENING_QR = "listeningQr"
        private const val LISTENING_SETS = "listeningSets"
        const val LESSONS_FILE = "lessons.json"
        const val LISTENING_FILE = "listening-tests.json"

        fun downloaded(context: Context, level: Level, file: String) = File(context.filesDir, "content/${level.code}/$file")

        /** The copy last downloaded from Firestore, or the bundled file before the first download. */
        fun read(context: Context, level: Level, file: String): String {
            val local = downloaded(context, level, file)
            if (local.exists()) runCatching { return local.readText() }
            return context.assets.open("content/${level.code}/$file").bufferedReader().use { it.readText() }
        }
    }
}
