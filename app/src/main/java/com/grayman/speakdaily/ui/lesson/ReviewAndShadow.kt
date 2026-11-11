package com.grayman.speakdaily.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grayman.speakdaily.app
import com.grayman.speakdaily.data.Lesson
import com.grayman.speakdaily.domain.TextMatcher
import com.grayman.speakdaily.speech.Accent
import com.grayman.speakdaily.speech.SpokenResult
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.SecondaryButton
import kotlinx.coroutines.launch

private data class ReviewItem(val en: String, val ko: String, val isDue: Boolean)

private const val XP_PER_SPOKEN = 5
private const val XP_GOOD_BONUS = 5
private const val GOOD_MATCH = 75
private const val REVIEW_LIMIT = 3

@Composable
fun ReviewStep(
    lesson: Lesson,
    accent: Accent,
    score: LessonScore,
    onProgress: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ReviewItem>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var match by remember { mutableStateOf<TextMatcher.Result?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val due = app.progress.dueCards(REVIEW_LIMIT).map { ReviewItem(it.en, it.ko, isDue = true) }
        // Nothing due yet (a new learner): preview today's phrases instead.
        items = due.ifEmpty { lesson.phrases.map { ReviewItem(it.en, it.ko, isDue = false) } }
    }
    val list = items ?: return
    val item = list.getOrNull(index)
    if (item == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    LaunchedEffect(index) {
        onProgress(index.toFloat() / list.size)
        match = null
        revealed = !item.isDue
        if (!item.isDue) app.voice.speak(item.en, accent)
    }

    fun advance() {
        index++
    }

    StepLayout(
        title = if (item.isDue) "복습: 기억나나요?" else "오늘의 표현",
        bottom = {
            val result = match
            if (result != null) {
                MatchFeedback(result, heard)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("다시 말하기", onClick = { match = null }, modifier = Modifier.weight(1f))
                    ChunkyButton("계속", onClick = ::advance, modifier = Modifier.weight(1f))
                }
            } else {
                SpeakPanel(
                    wantRecording = false,
                    onResult = { spoken ->
                        val compared = TextMatcher.compare(item.en, spoken.text)
                        match = compared
                        heard = spoken.text
                        revealed = true
                        score.addSpoken(spoken)
                        if (compared.score >= GOOD_MATCH) score.xp += XP_PER_SPOKEN
                        if (item.isDue) scope.launch { app.progress.gradeCard(item.en, compared.score) }
                    },
                    onSkip = ::advance,
                )
            }
        },
    ) {
        Text(
            if (item.isDue) "이 뜻을 영어로 말해 보세요" else "듣고 따라 말해 보세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhraseCard(item.ko, if (revealed) item.en else null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                if (revealed) "다시 듣기" else "힌트 듣기",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onClick = {
                    revealed = true
                    scope.launch { app.voice.speak(item.en, accent) }
                },
            )
        }
    }
}

@Composable
private fun PhraseCard(primary: String, secondary: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(primary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (secondary != null) {
            Text(secondary, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MatchFeedback(result: TextMatcher.Result, heard: String) {
    val good = result.score >= GOOD_MATCH
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (good) Game.GoodSoft else Game.BadSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            if (good) "좋아요! ${result.score}% 알아들었어요" else "${result.score}% 알아들었어요",
            style = MaterialTheme.typography.titleMedium,
            color = if (good) Game.OnGoodSoft else Game.OnBadSoft,
        )
        Text(
            "들린 말: \"$heard\"",
            style = MaterialTheme.typography.bodyMedium,
            color = if (good) Game.OnGoodSoft else Game.OnBadSoft,
        )
        if (result.missing.isNotEmpty()) {
            Text(
                "놓친 단어: " + result.missing.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (good) Game.OnGoodSoft else Game.OnBadSoft,
            )
        }
    }
}

fun LessonScore.addSpoken(result: SpokenResult) {
    // Some recognizers report no timing; estimate from words at a typical learner pace.
    val seconds = result.speechSeconds ?: (TextMatcher.tokenize(result.text).size / 2.0)
    speakingSeconds += seconds.toFloat()
}

@Composable
fun ShadowStep(
    lesson: Lesson,
    accent: Accent,
    score: LessonScore,
    onProgress: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var rate by remember { mutableStateOf(1f) }
    var match by remember { mutableStateOf<TextMatcher.Result?>(null) }
    var spoken by remember { mutableStateOf<SpokenResult?>(null) }
    var awarded by remember { mutableStateOf(false) }

    val line = lesson.lines[lesson.shadow[index]]

    LaunchedEffect(index) {
        onProgress(index.toFloat() / lesson.shadow.size)
        match = null
        spoken = null
        awarded = false
        app.voice.speak(line.text, accent, line.speaker, rate)
    }

    fun advance() {
        if (index + 1 < lesson.shadow.size) index++ else onDone()
    }

    StepLayout(
        title = "쉐도잉",
        bottom = {
            val result = match
            if (result != null) {
                MatchFeedback(result, spoken?.text.orEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("다시 말하기", onClick = { match = null }, modifier = Modifier.weight(1f))
                    ChunkyButton("계속", onClick = ::advance, modifier = Modifier.weight(1f))
                }
            } else {
                SpeakPanel(
                    wantRecording = true,
                    onResult = { heard ->
                        val compared = TextMatcher.compare(line.text, heard.text)
                        match = compared
                        spoken = heard
                        score.addSpoken(heard)
                        // Retrying is encouraged, so XP is earned once per sentence.
                        if (!awarded) {
                            awarded = true
                            score.xp += XP_PER_SPOKEN + if (compared.score >= GOOD_MATCH) XP_GOOD_BONUS else 0
                        }
                    },
                    onSkip = ::advance,
                )
            }
        },
    ) {
        Text(
            "소리와 리듬을 그대로 따라 해 보세요 (${index + 1}/${lesson.shadow.size})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(highlighted(line.text, match?.missing.orEmpty()), style = MaterialTheme.typography.titleLarge)
            Text(line.ko, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "모범 음성",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onClick = { scope.launch { app.voice.speak(line.text, accent, line.speaker, rate) } },
            )
            SecondaryButton(
                if (rate == 1f) "느리게" else "보통 속도",
                icon = Icons.Rounded.Speed,
                onClick = {
                    rate = if (rate == 1f) 0.75f else 1f
                    scope.launch { app.voice.speak(line.text, accent, line.speaker, rate) }
                },
            )
        }
        if (spoken?.hasRecording == true) {
            SecondaryButton(
                "내 녹음 듣기",
                icon = Icons.Rounded.PlayArrow,
                onClick = app.speech::playLastRecording,
            )
        }
    }
}

/** Marks the words the recognizer missed in the sentence itself. */
private fun highlighted(sentence: String, missing: List<String>) = buildAnnotatedString {
    val remaining = missing.toMutableList()
    sentence.split(" ").forEachIndexed { i, word ->
        if (i > 0) append(" ")
        val token = TextMatcher.tokenize(word).firstOrNull()
        if (token != null && remaining.remove(token)) {
            withStyle(SpanStyle(color = Game.Bad, fontWeight = FontWeight.ExtraBold)) { append(word) }
        } else {
            append(word)
        }
    }
}
