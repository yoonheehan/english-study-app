package com.grayman.englishstudy.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One spoken answer choice, with a translation for the review screen. */
@Serializable
data class SpokenOption(val text: String, val ko: String)

/** Part 1: hear a question or remark, then pick the best of three spoken responses. */
@Serializable
data class QuestionResponseItem(
    val id: String,
    val prompt: String,
    val promptKo: String,
    val options: List<SpokenOption>,
    val answer: Int,
)

@Serializable
data class SetQuestion(
    val q: String,
    val qKo: String,
    val options: List<String>,
    val answer: Int,
)

@Serializable
enum class SetType {
    @SerialName("conversation")
    CONVERSATION,

    @SerialName("talk")
    TALK,
}

/** Parts 2 and 3: one recording followed by three printed questions. */
@Serializable
data class ListeningSet(
    val id: String,
    val type: SetType,
    val titleKo: String,
    val lines: List<Line>,
    val questions: List<SetQuestion>,
)

@Serializable
data class ListeningPool(
    val qr: List<QuestionResponseItem>,
    val sets: List<ListeningSet>,
)

object ListeningParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(text: String): ListeningPool = json.decodeFromString(text)
}

class ListeningRepository(private val context: Context) {
    private val pools = mutableMapOf<Level, ListeningPool>()

    fun pool(level: Level): ListeningPool = pools.getOrPut(level) {
        ListeningParser.parse(ContentSync.read(context, level, ContentSync.LISTENING_FILE))
    }
}

/** Speech rate grows with level, so advanced tests sound closer to natural speed. */
fun Level.listeningRate(): Float = when (this) {
    Level.A1 -> 0.85f
    Level.A2 -> 0.9f
    Level.B1 -> 1.0f
    Level.B2 -> 1.05f
    Level.C1 -> 1.1f
    Level.C2 -> 1.15f
    Level.D -> 1.1f
    Level.E -> 1.05f
    Level.F -> 1.2f
}
