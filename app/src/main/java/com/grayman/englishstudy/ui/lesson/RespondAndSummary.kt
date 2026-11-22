package com.grayman.englishstudy.ui.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.data.Lesson
import com.grayman.englishstudy.data.ResponseCard
import com.grayman.englishstudy.domain.ResponseScorer
import com.grayman.englishstudy.domain.Streak
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.speech.SpokenResult
import com.grayman.englishstudy.ui.Chip
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Confetti
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.Mascot
import com.grayman.englishstudy.ui.MascotMood
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.SpeechBubble
import kotlinx.coroutines.launch

private const val BONUS_XP = 15
private const val COMPLETION_XP = 10

@Composable
fun RespondStep(card: ResponseCard, accent: Accent, score: LessonScore, onDone: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var result by remember(card) { mutableStateOf<ResponseScorer.Result?>(null) }
    var transcript by remember(card) { mutableStateOf("") }
    var previousTotal by remember(card) { mutableStateOf<Int?>(null) }
    var lastTotal by remember(card) { mutableStateOf<Int?>(null) }
    var bestAwarded by remember(card) { mutableIntStateOf(0) }

    LaunchedEffect(card) { app.voice.speak(card.prompt, accent, speaker = 0) }

    fun onSpoken(spoken: SpokenResult) {
        val scored = ResponseScorer.score(
            ResponseScorer.Input(
                transcript = spoken.text,
                keywords = card.keywords,
                sampleAnswers = card.answers.map { it.text },
                speechSeconds = spoken.speechSeconds,
                confidence = spoken.confidence,
            ),
        )
        // Kept apart from result, which is cleared while the learner retries.
        previousTotal = lastTotal
        lastTotal = scored.total
        result = scored
        transcript = spoken.text
        score.addSpoken(spoken)
        score.bestResponse = maxOf(score.bestResponse, scored.total)
        // XP follows the best attempt, so a retry can only help.
        val earned = scored.total / 5
        if (earned > bestAwarded) {
            score.xp += earned - bestAwarded
            bestAwarded = earned
        }
    }

    StepLayout(
        title = "대답 연습",
        bottom = {
            if (result == null) {
                SpeakPanel(wantRecording = false, onResult = ::onSpoken, onSkip = onDone)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        "다시 말하기",
                        icon = Icons.Rounded.Mic,
                        onClick = { result = null },
                        modifier = Modifier.weight(1f),
                    )
                    ChunkyButton("계속", onClick = onDone, modifier = Modifier.weight(1f))
                }
            }
        },
    ) {
        Text(card.situationKo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button) {
                    scope.launch { app.voice.speak(card.prompt, accent, speaker = 0) }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "다시 듣기", tint = Game.Lagoon, modifier = Modifier.size(24.dp))
            Spacer8()
            Text("\"${card.prompt}\"", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        val scored = result
        if (scored != null) {
            ScoreCard(scored, previousTotal, transcript)
            Text("이렇게도 말할 수 있어요 (누르면 들려요)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            card.answers.forEach { answer ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(role = Role.Button) {
                            scope.launch { app.voice.speak(answer.text, accent, speaker = 1) }
                        }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(answer.tone, Game.LagoonSoft, Game.LagoonEdge)
                        answer.region?.let { Chip(it, Game.Coral.copy(alpha = 0.18f), Game.CoralEdge) }
                    }
                    Text(answer.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(answer.noteKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Spacer8() = Box(Modifier.width(8.dp))

@Composable
private fun ScoreCard(result: ResponseScorer.Result, previousTotal: Int?, transcript: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${result.total}점", style = MaterialTheme.typography.headlineLarge, color = scoreColor(result.total))
            Spacer8()
            previousTotal?.let { previous ->
                val delta = result.total - previous
                Chip(
                    if (delta >= 0) "지난번보다 +$delta" else "지난번보다 $delta",
                    if (delta >= 0) Game.GoodSoft else Game.BadSoft,
                    if (delta >= 0) Game.OnGoodSoft else Game.OnBadSoft,
                )
            }
        }
        Text("내가 한 말: \"$transcript\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScoreBar("상황에 맞는 대답", result.fit)
        ScoreBar("표현의 풍부함", result.expression)
        ScoreBar("유창성", result.fluency)
        ScoreBar("알아들음", result.clarity)
        result.tips.forEach { tip ->
            Text(tipText(tip, result), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            "부분 채점: 문법은 아직 평가하지 않아요. 내 변화를 보는 참고 점수예요.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreBar(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        GameProgressBar(value / 100f, Modifier.weight(1f), color = scoreColor(value), height = 10.dp)
    }
}

private fun scoreColor(value: Int): Color = when {
    value >= 75 -> Game.Good
    value >= 50 -> Game.Flame
    else -> Game.Bad
}

private fun tipText(tip: ResponseScorer.Tip, result: ResponseScorer.Result): String = when (tip) {
    ResponseScorer.Tip.SAY_MORE -> "한 문장만 더 붙여 보세요. 대화가 훨씬 자연스러워져요."
    ResponseScorer.Tip.USE_KEYWORD -> "상황과 조금 멀어요. 아래 예시에서 표현을 하나 골라 말해 보세요."
    ResponseScorer.Tip.SPEAK_FASTER -> "조금 더 끊지 않고 말해 보세요. (분당 ${result.wordsPerMinute}단어)"
    ResponseScorer.Tip.SLOW_DOWN -> "조금만 천천히 말하면 더 또렷해져요. (분당 ${result.wordsPerMinute}단어)"
    ResponseScorer.Tip.CLEARER -> "조금 더 또렷하게 말해 보세요."
    ResponseScorer.Tip.GREAT -> "자연스러운 대답이에요! 다른 말투로도 말해 보면 더 좋아요."
}

@Composable
fun SummaryStep(lesson: Lesson, accent: Accent, score: LessonScore, onExit: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var bonusCard by remember { mutableStateOf<ResponseCard?>(null) }
    var bonusRounds by remember { mutableIntStateOf(0) }
    val records by app.progress.dailyRecords.collectAsState(initial = emptyList())
    val streak = Streak.current(records.filter { it.xp > 0 }.map { it.epochDay }, app.progress.today())

    LaunchedEffect(Unit) {
        // Finishing always earns something, so a hard lesson still keeps the streak alive.
        score.xp += COMPLETION_XP
        app.progress.completeLesson(lesson, score.xp, score.speakingSeconds.toInt(), score.bestResponse)
    }

    val bonus = bonusCard
    if (bonus != null) {
        val bonusScore = remember(bonus) { LessonScore() }
        RespondStep(
            card = bonus,
            accent = accent,
            score = bonusScore,
            onDone = {
                val earned = bonusScore.xp + BONUS_XP
                score.xp += earned
                score.speakingSeconds += bonusScore.speakingSeconds
                bonusRounds++
                bonusCard = null
                scope.launch {
                    app.progress.addActivity(
                        ActivityKind.BONUS,
                        "한 판 더 · 대답 연습",
                        "말한 시간 ${bonusScore.speakingSeconds.toInt()}초",
                        earned,
                        bonusScore.speakingSeconds.toInt(),
                    )
                }
            },
        )
        return
    }

    val shownXp = remember { Animatable(0f) }
    LaunchedEffect(score.xp) { shownXp.animateTo(score.xp.toFloat(), tween(900)) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Mascot(MascotMood.HAPPY, size = 170.dp)
            Text(
                if (bonusRounds > 0) "한 판 더, 멋져요!" else "오늘 연습 완료!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            // Equal height keeps the tiles aligned when one label wraps to two lines.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultTile(Icons.Rounded.Bolt, Game.Xp, "+${shownXp.value.toInt()}", "XP", Modifier.weight(1f))
                ResultTile(Icons.Rounded.LocalFireDepartment, Game.Flame, "${streak}일", "연속", Modifier.weight(1f))
                ResultTile(Icons.Rounded.Mic, Game.Coral, formatSeconds(score.speakingSeconds.toInt()), "말한 시간", Modifier.weight(1f))
            }
            SpeechBubble("새 표현 ${lesson.phrases.size}개를 복습 카드에 넣었어요. 내일 다시 만나요!", Modifier.fillMaxWidth())

            ChunkyButton(
                "한 판 더 (대답 연습)",
                color = Game.Coral,
                edge = Game.CoralEdge,
                icon = Icons.Rounded.Mic,
                onClick = {
                    val pool = app.content.units.first { it.level == app.content.levelOf(lesson.id) }.lessons
                        .map { it.response }
                        .filter { it != lesson.response }
                    bonusCard = pool.random()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton("완료", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
        if (bonusRounds == 0) Confetti()
    }
}

@Composable
private fun ResultTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSeconds(seconds: Int): String =
    if (seconds < 60) "${seconds}초" else "${seconds / 60}분 ${seconds % 60}초"
