package com.grayman.englishstudy.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.Lesson
import com.grayman.englishstudy.domain.TextMatcher
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.AnswerTile
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.PlaybackWaveform
import com.grayman.englishstudy.ui.SecondaryButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class ListenPhase { LISTEN, GIST, DICTATION, SCRIPT }

private const val XP_CORRECT = 10

@Composable
fun ListenStep(
    lesson: Lesson,
    accent: Accent,
    canChangeAccent: Boolean,
    onChangeAccent: () -> Unit,
    score: LessonScore,
    onProgress: (Float) -> Unit,
    onDone: () -> Unit,
) {
    var phase by remember { mutableStateOf(ListenPhase.LISTEN) }
    LaunchedEffect(phase) { onProgress(phase.ordinal / ListenPhase.entries.size.toFloat()) }

    when (phase) {
        ListenPhase.LISTEN -> ListenPhaseContent(lesson, accent, canChangeAccent, onChangeAccent) {
            phase = ListenPhase.GIST
        }

        ListenPhase.GIST -> GistPhase(lesson, accent, score) { phase = ListenPhase.DICTATION }
        ListenPhase.DICTATION -> DictationPhase(lesson, accent, score) { phase = ListenPhase.SCRIPT }
        ListenPhase.SCRIPT -> ScriptPhase(lesson, accent, onDone)
    }
}

/** Plays the whole dialogue. One job at a time, so a replay never talks over itself. */
@Composable
private fun rememberDialoguePlayer(lesson: Lesson): DialoguePlayer {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    return remember(lesson.id) {
        DialoguePlayer { accent, rate, onFinished ->
            scope.launch {
                lesson.lines.forEach { line -> app.voice.speak(line.text, accent, line.speaker, rate) }
                onFinished()
            }
        }
    }
}

private class DialoguePlayer(private val launch: (Accent, Float, () -> Unit) -> Job) {
    private var job: Job? = null
    var playing by mutableStateOf(false)
        private set

    fun play(accent: Accent, rate: Float = 1f) {
        job?.cancel()
        playing = true
        job = launch(accent, rate) { playing = false }
    }
}

@Composable
private fun ListenPhaseContent(
    lesson: Lesson,
    accent: Accent,
    canChangeAccent: Boolean,
    onChangeAccent: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val voice = context.app.voice
    val notInstalled by voice.notInstalled.collectAsState()
    // Picks up voices installed on the system screen as soon as the learner comes back.
    LifecycleResumeEffect(Unit) {
        voice.refreshVoices()
        onPauseOrDispose { }
    }
    val player = rememberDialoguePlayer(lesson)
    var heard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        player.play(accent)
        heard = true
    }

    StepLayout(
        title = "집중 듣기",
        bottom = {
            ChunkyButton(
                if (player.playing) "듣는 중..." else "문제 풀기",
                onClick = onContinue,
                enabled = !player.playing,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Text(lesson.settingKo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "대본 없이 들어 보세요. 다 못 알아들어도 괜찮아요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlaybackWaveform(player.playing, Modifier.size(width = 128.dp, height = 72.dp))
        }
        AccentBadge(accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("다시 듣기", icon = Icons.Rounded.Replay, onClick = { player.play(accent) })
            SecondaryButton("느리게", icon = Icons.Rounded.Speed, onClick = { player.play(accent, 0.75f) })
        }
        if (canChangeAccent) {
            SecondaryButton(
                "다른 억양으로 듣기",
                icon = Icons.Rounded.Public,
                onClick = onChangeAccent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (notInstalled.isNotEmpty()) {
            Text(
                "더 받을 수 있는 억양: " + notInstalled.joinToString(", ") { it.labelKo },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                "억양 음성 받기",
                icon = Icons.Rounded.Download,
                onClick = {
                    runCatching { context.startActivity(context.app.voice.installVoicesIntent()) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    // Hearing the same dialogue in a new accent is the point of switching, so it plays at once.
    var lastAccent by remember { mutableStateOf(accent) }
    LaunchedEffect(accent) {
        if (accent != lastAccent) {
            lastAccent = accent
            player.play(accent)
        }
    }
}

@Composable
fun AccentBadge(accent: Accent) {
    Row(
        Modifier
            .background(Game.LagoonSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Public, null, tint = Game.LagoonEdge)
        Text("오늘의 억양: ${accent.labelKo}", color = Game.LagoonEdge, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GistPhase(lesson: Lesson, accent: Accent, score: LessonScore, onContinue: () -> Unit) {
    val player = rememberDialoguePlayer(lesson)
    var picked by remember { mutableStateOf<Int?>(null) }
    val gist = lesson.gist
    StepLayout(
        title = "무슨 내용이었나요?",
        bottom = {
            val choice = picked
            if (choice != null) {
                ResultBar(
                    correct = choice == gist.answer,
                    correctText = "정답이에요! +$XP_CORRECT XP",
                    wrongText = "정답: ${gist.options[gist.answer]}",
                )
                ChunkyButton("계속", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Text(gist.questionKo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        gist.options.forEachIndexed { index, option ->
            AnswerTile(
                text = option,
                selected = picked == index,
                revealCorrect = picked != null && index == gist.answer,
                revealWrong = picked == index && index != gist.answer,
                enabled = picked == null,
                onClick = {
                    picked = index
                    if (index == gist.answer) score.xp += XP_CORRECT
                },
            )
        }
        if (picked == null) {
            SecondaryButton("한 번 더 듣기", icon = Icons.Rounded.Replay, onClick = { player.play(accent) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictationPhase(lesson: Lesson, accent: Accent, score: LessonScore, onContinue: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val line = lesson.dictationLine()
    val answer = remember(lesson.id) { TextMatcher.tokenize(line.text) }
    // Tiles keep their index, because a sentence can use the same word twice.
    val bank = remember(lesson.id) {
        (answer + lesson.dictation.distractors.map { it.lowercase() }).withIndex().shuffled(kotlin.random.Random(lesson.id.hashCode()))
    }
    val placed = remember(lesson.id) { mutableStateListOf<IndexedValue<String>>() }
    var checked by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.voice.speak(line.text, accent, line.speaker) }

    StepLayout(
        title = "들리는 대로 맞춰 보세요",
        bottom = {
            if (checked) {
                ResultBar(correct, "완벽해요! +$XP_CORRECT XP", "정답: ${line.text}")
                ChunkyButton("계속", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            } else {
                ChunkyButton(
                    "확인",
                    enabled = placed.isNotEmpty(),
                    onClick = {
                        correct = placed.map { it.value } == answer
                        checked = true
                        if (correct) score.xp += XP_CORRECT
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "듣기",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onClick = { scope.launch { app.voice.speak(line.text, accent, line.speaker) } },
            )
            SecondaryButton(
                "느리게",
                icon = Icons.Rounded.Speed,
                onClick = { scope.launch { app.voice.speak(line.text, accent, line.speaker, 0.7f) } },
            )
        }
        FlowRow(
            Modifier
                .fillMaxWidth()
                // Reserves the rows the full answer needs, so the word bank never shifts under a finger.
                .heightIn(min = trayHeightFor(answer.size))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            placed.toList().forEach { tile ->
                WordTile(tile.value, enabled = !checked) { placed.remove(tile) }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bank.forEach { tile ->
                val used = tile in placed
                WordTile(tile.value, enabled = !used && !checked, faded = used) { placed.add(tile) }
            }
        }
    }
}

@Composable
private fun WordTile(text: String, enabled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (faded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            displayWord(text),
            style = MaterialTheme.typography.bodyLarge,
            color = if (faded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun trayHeightFor(words: Int) = when {
    words <= 6 -> 64.dp
    words <= 12 -> 104.dp
    words <= 18 -> 144.dp
    else -> 184.dp
}

/** Tokens are lowercase for comparing; the pronoun I still reads as I on screen. */
private fun displayWord(word: String): String =
    if (word == "i" || word.startsWith("i'")) word.replaceFirstChar { it.uppercase() } else word

@Composable
private fun ScriptPhase(lesson: Lesson, accent: Accent, onContinue: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var playingIndex by remember { mutableIntStateOf(-1) }
    StepLayout(
        title = "대본 확인",
        bottom = { ChunkyButton("쉐도잉 하러 가기", onClick = onContinue, modifier = Modifier.fillMaxWidth()) },
    ) {
        Text(
            "문장을 누르면 다시 들을 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lesson.lines.forEachIndexed { index, line ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            playingIndex == index -> Game.LagoonSoft
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .clickable(role = Role.Button) {
                        scope.launch {
                            playingIndex = index
                            app.voice.speak(line.text, accent, line.speaker)
                            playingIndex = -1
                        }
                    }
                    .padding(12.dp),
            ) {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playingIndex == index) Game.LagoonEdge else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    line.ko,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (playingIndex == index) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ResultBar(correct: Boolean, correctText: String, wrongText: String) {
    Text(
        if (correct) correctText else wrongText,
        style = MaterialTheme.typography.titleMedium,
        color = if (correct) Game.OnGoodSoft else Game.OnBadSoft,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (correct) Game.GoodSoft else Game.BadSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
    )
}
