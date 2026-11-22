package com.grayman.englishstudy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Level(val code: String, val labelKo: String, val descriptionKo: String) {
    A1("a1", "입문", "짧은 문장과 기본 인사부터"),
    A2("a2", "초급", "일상 상황에서 간단히 말하기"),
    B1("b1", "중급", "여행과 일상 대화를 편하게"),
    B2("b2", "중고급", "회사에서 의견을 분명하게"),
    C1("c1", "고급", "협상, 토론, 미묘한 표현까지"),
    C2("c2", "최상급", "원어민 속도와 숨은 뉘앙스까지"),
    D("d", "전문 분야", "법률, 의료, 금융, IT 현장의 대화"),
    E("e", "수사와 문학", "명연설, 고전 문학, 설득의 기술"),
    F("f", "원어민 감각", "슬랭, 유머, 지역 억양, 숨은 뜻"),
    ;

    companion object {
        fun fromCode(code: String): Level = entries.firstOrNull { it.code == code } ?: B1
    }
}

@Serializable
data class Line(val speaker: Int, val text: String, val ko: String)

@Serializable
data class GistQuestion(val questionKo: String, val options: List<String>, val answer: Int)

@Serializable
data class Dictation(val line: Int, val distractors: List<String>)

@Serializable
data class Phrase(val en: String, val ko: String)

@Serializable
data class SampleAnswer(val text: String, val tone: String, val noteKo: String, val region: String? = null)

@Serializable
data class ResponseCard(
    val situationKo: String,
    val prompt: String,
    val keywords: List<String>,
    val answers: List<SampleAnswer>,
)

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val titleKo: String,
    val icon: String,
    val settingKo: String,
    val lines: List<Line>,
    val gist: GistQuestion,
    val dictation: Dictation,
    val shadow: List<Int>,
    val phrases: List<Phrase>,
    val response: ResponseCard,
)

data class LevelUnit(val level: Level, val lessons: List<Lesson>)

/** Parses the bundled lesson JSON. Separate from Android so tests can validate the real files. */
object ContentParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parseLessons(text: String): List<Lesson> = json.decodeFromString(text)
}

class ContentRepository(private val context: Context) {
    /** Read once per start from the Firestore copy (see ContentSync), so new content shows on the next start. */
    val units: List<LevelUnit> by lazy {
        Level.entries.map { level ->
            LevelUnit(level, ContentParser.parseLessons(ContentSync.read(context, level, ContentSync.LESSONS_FILE)))
        }
    }

    fun lesson(id: String): Lesson = units.flatMap { it.lessons }.first { it.id == id }

    fun levelOf(lessonId: String): Level = units.first { unit -> unit.lessons.any { it.id == lessonId } }.level
}
