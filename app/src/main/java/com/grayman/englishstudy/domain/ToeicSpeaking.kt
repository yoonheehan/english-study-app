package com.grayman.englishstudy.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * TOEIC Speaking-style practice, following the official format (ETS): 11 questions in 5 parts.
 * Q1-2 read aloud (45s prep, 45s speak), Q3-4 describe a picture (45s / 30s),
 * Q5-7 respond to questions (3s / 15, 15, 30s), Q8-10 respond using information
 * (45s to read, 3s / 15, 15, 30s), Q11 express an opinion (45s / 60s).
 * All questions are written for this app (see [ToeicContent]); none are copied from real tests.
 */
@Serializable
enum class ToeicPart(val titleKo: String, val guideKo: String, val maxScore: Int) {
    READ_ALOUD("지문 읽기", "화면의 글을 소리 내어 읽어요. 발음, 억양, 강세를 봐요.", 3),
    PICTURE("사진 묘사", "그림을 보고 무엇이 보이는지 최대한 자세히 말해요.", 3),
    RESPOND("질문에 답하기", "전화 설문에 답하듯 질문 3개에 바로 대답해요.", 3),
    INFO("정보 보고 답하기", "일정표를 읽고, 그 정보를 묻는 질문에 답해요.", 3),
    OPINION("의견 말하기", "주제에 대한 내 의견을 이유와 예를 들어 1분 동안 말해요.", 5),
}

/** How hard the questions and model answers are. The test format and timing stay the same. */
@Serializable
enum class ToeicLevel(val titleKo: String, val targetKo: String, val guideKo: String, val targetEn: String) {
    BASIC("기초", "IM1~IM2 목표 · 110~120점", "짧은 지문, 한두 사람이 나오는 사진, 일상 주제. 예시 답변도 쉬운 문장이에요.", "IM1-IM2 (simple, clear sentences)"),
    MIDDLE("중급", "IM3~IH 목표 · 130~150점", "회사·여행·생활 등 시험에 자주 나오는 주제. 이유와 예를 드는 연습이에요.", "IH (natural sentences with reasons and examples)"),
    ADVANCED("고급", "AL 이상 목표 · 160점+", "긴 지문과 숫자, 복잡한 사진, 변경 사항이 있는 일정, 사회적인 의견 질문이에요.", "AL-AM (varied, precise vocabulary and well-organized ideas)"),
}

/** One question to answer: what to show, what is asked, and how long to prepare and speak. */
data class ToeicQuestion(
    val number: Int,
    val level: ToeicLevel,
    val part: ToeicPart,
    val prepSeconds: Int,
    val speakSeconds: Int,
    /** Read aloud on screen, a picture, an intro, or a schedule. */
    val display: ToeicDisplay,
    /** Spoken by the app before preparation starts (questions in parts 3 and 4). */
    val spokenPrompt: String?,
    /** Shown text of the question, when there is one. */
    val questionText: String?,
    /** For read-aloud: the text; otherwise a model answer to compare with. */
    val modelAnswer: String,
    /** Words a good answer is likely to use; used for the offline estimate. */
    val keywords: List<String>,
)

sealed interface ToeicDisplay {
    data class Text(val text: String) : ToeicDisplay
    data class Picture(val photo: Photo) : ToeicDisplay
    data class Intro(val text: String) : ToeicDisplay
    data class Schedule(val title: String, val rows: List<Pair<String, String>>, val note: String) : ToeicDisplay
}

/** A real photo in assets/toeic/photos, with a model description and the words a good answer uses. */
data class Photo(val file: String, val credit: String, val description: String, val keywords: List<String>) {
    val assetPath: String get() = "toeic/photos/$file"
}

object ToeicSpeaking {
    /** A full mock test of 11 questions at [level], or every question of one part. */
    fun build(part: ToeicPart?, level: ToeicLevel = ToeicLevel.MIDDLE, random: Random = Random(System.nanoTime())): List<ToeicQuestion> {
        val out = mutableListOf<ToeicQuestion>()
        fun add(q: (Int) -> ToeicQuestion) = out.add(q(out.size + 1))
        val parts = part?.let { listOf(it) } ?: ToeicPart.entries
        for (p in parts) when (p) {
            ToeicPart.READ_ALOUD -> ToeicContent.readAloud.getValue(level).shuffled(random).take(2).forEach { text ->
                add { n -> ToeicQuestion(n, level, p, 45, 45, ToeicDisplay.Text(text), null, null, text, emptyList()) }
            }
            ToeicPart.PICTURE -> ToeicContent.pictures.getValue(level).shuffled(random).take(2).forEach { photo ->
                add { n -> ToeicQuestion(n, level, p, 45, 30, ToeicDisplay.Picture(photo), null, "Describe the picture in as much detail as you can.", photo.description, photo.keywords) }
            }
            ToeicPart.RESPOND -> ToeicContent.respondSets.getValue(level).random(random).let { set ->
                set.questions.forEachIndexed { i, q ->
                    add { n -> ToeicQuestion(n, level, p, 3, if (i == 2) 30 else 15, ToeicDisplay.Intro(set.intro), q, q, set.samples[i], keywordsOf(set.samples[i])) }
                }
            }
            ToeicPart.INFO -> ToeicContent.infoSets.getValue(level).random(random).let { set ->
                set.questions.forEachIndexed { i, q ->
                    // The 45 seconds to read the schedule come before the first question.
                    add { n ->
                        ToeicQuestion(
                            n, level, p, if (i == 0) 45 else 3, if (i == 2) 30 else 15,
                            ToeicDisplay.Schedule(set.title, set.rows, set.note),
                            (if (i == 0) set.intro + " " else "") + q, null, set.answers[i], set.keywords[i],
                        )
                    }
                }
            }
            ToeicPart.OPINION -> ToeicContent.opinions.getValue(level).random(random).let { o ->
                add { n -> ToeicQuestion(n, level, p, 45, 60, ToeicDisplay.Text(o.prompt), o.prompt, o.prompt, o.sample, listOf("because", "for example", "first", "also", "so")) }
            }
        }
        return out
    }

    /** How many different items one part has at [level], to show how varied the practice is. */
    fun poolSize(part: ToeicPart, level: ToeicLevel): Int = when (part) {
        ToeicPart.READ_ALOUD -> ToeicContent.readAloud.getValue(level).size
        ToeicPart.PICTURE -> ToeicContent.pictures.getValue(level).size
        ToeicPart.RESPOND -> ToeicContent.respondSets.getValue(level).size
        ToeicPart.INFO -> ToeicContent.infoSets.getValue(level).size
        ToeicPart.OPINION -> ToeicContent.opinions.getValue(level).size
    }

    private val stop = setOf("the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "is", "are", "it", "i", "my", "for", "with", "so", "that", "this", "you", "be")

    private fun keywordsOf(sample: String) =
        TextMatcher.tokenize(sample).filter { it.length > 3 && it !in stop }.distinct().take(8)

    /**
     * A rough offline score on the part's scale, from how much was said and how many key words it used.
     * Read-aloud uses the word match against the text. The AI review gives the real judgment.
     */
    fun estimate(q: ToeicQuestion, spoken: String): Int {
        val max = q.part.maxScore
        if (spoken.isBlank()) return 0
        if (q.part == ToeicPart.READ_ALOUD) {
            val pct = TextMatcher.compare(q.modelAnswer, spoken).score
            return when {
                pct >= 90 -> 3
                pct >= 70 -> 2
                pct >= 40 -> 1
                else -> 0
            }
        }
        val words = TextMatcher.tokenize(spoken)
        val targetWords = (q.speakSeconds * 1.6).toInt() // about 100 words a minute is a full answer
        val amount = (words.size.toFloat() / targetWords).coerceAtMost(1f)
        val hits = q.keywords.count { k -> words.any { it.startsWith(k.lowercase().take(5)) } }
        val coverage = if (q.keywords.isEmpty()) amount else hits.toFloat() / q.keywords.size.coerceAtMost(4)
        val raw = 0.6f * amount + 0.4f * coverage.coerceAtMost(1f)
        return (raw * max + 0.49f).toInt().coerceIn(if (words.size >= 3) 1 else 0, max)
    }

    /** Reads "점수: N/M" (or "N/M") from the AI review; null when the reply has no score. */
    fun parseScore(review: String, max: Int): Int? {
        val m = Regex("""점수\s*[:：]?\s*(\d+)\s*/\s*(\d+)""").find(review)
            ?: Regex("""(\d+)\s*/\s*$max\b""").find(review)
            ?: return null
        return m.groupValues[1].toIntOrNull()?.coerceIn(0, max)
    }

    /**
     * A rough TOEIC Speaking score (0-200, in steps of 10) from the share of points earned.
     * Not an official conversion: ETS weighs tasks and raters differently. It answers "about where am I".
     */
    fun estimateScaled(points: Int, maxPoints: Int): Int {
        if (maxPoints <= 0) return 0
        return ((points.toFloat() / maxPoints) * 20f + 0.5f).toInt().coerceIn(0, 20) * 10
    }

    /** The grade bands commonly reported for TOEIC Speaking in Korea (AH ... NL), with a Korean note. */
    fun grade(scaled: Int): Pair<String, String> = when {
        scaled >= 200 -> "AH" to "원어민에 가까운 수준"
        scaled >= 180 -> "AM" to "복잡한 의견도 막힘없이"
        scaled >= 160 -> "AL" to "회사에서 대부분 영어로 업무 가능"
        scaled >= 140 -> "IH" to "의견과 이유를 꽤 자연스럽게"
        scaled >= 130 -> "IM3" to "대부분 질문에 문장으로 답변"
        scaled >= 120 -> "IM2" to "익숙한 주제는 무리 없이"
        scaled >= 110 -> "IM1" to "짧은 문장으로 의사소통"
        scaled >= 90 -> "IL" to "기본 문장은 되지만 자주 막힘"
        scaled >= 60 -> "NH" to "단어와 짧은 구 위주"
        scaled >= 30 -> "NM" to "단어 몇 개로 답하는 수준"
        else -> "NL" to "아직 답을 만들기 어려운 단계"
    }

    /** The AI review: TOEIC-style rubric, in Korean, with a better answer. */
    fun reviewPrompt(q: ToeicQuestion, spoken: String): String {
        val task = when (val d = q.display) {
            is ToeicDisplay.Text -> if (q.part == ToeicPart.READ_ALOUD) "Read this text aloud: \"${d.text}\"" else "Opinion question: ${d.text}"
            is ToeicDisplay.Picture -> "Describe a picture. The picture shows: ${q.modelAnswer}"
            is ToeicDisplay.Intro -> "Situation: ${d.text}\nQuestion: ${q.spokenPrompt}"
            is ToeicDisplay.Schedule -> "Schedule: ${d.title}; " + d.rows.joinToString("; ") { "${it.first} ${it.second}" } + "; ${d.note}\nQuestion: ${q.spokenPrompt}"
        }
        return """
            You are a TOEIC Speaking rater and coach for a Korean learner.
            Task (${q.part.name}, ${q.speakSeconds} seconds to speak): $task
            The learner's answer, written by speech recognition (ignore spelling and punctuation): "$spoken"
            Score it on the official scale 0-${q.part.maxScore}, judging task completion, grammar, vocabulary, and coherence${if (q.part == ToeicPart.READ_ALOUD) ", and which words seem misread" else ""}.
            Reply in Korean, in exactly this format:
            점수: N/${q.part.maxScore}
            좋은 점: one sentence
            고칠 점: one or two specific points
            더 좋은 답변: a natural English answer the learner could realistically say next, aimed at ${q.level.targetEn}${if (q.part == ToeicPart.READ_ALOUD) " (instead, list the words to practice)" else ""}
        """.trimIndent()
    }
}
