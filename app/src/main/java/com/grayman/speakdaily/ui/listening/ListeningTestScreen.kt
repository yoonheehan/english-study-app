package com.grayman.speakdaily.ui.listening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grayman.speakdaily.app
import com.grayman.speakdaily.data.Level
import com.grayman.speakdaily.data.listeningRate
import com.grayman.speakdaily.domain.ListeningTestBuilder
import com.grayman.speakdaily.domain.TestLength
import com.grayman.speakdaily.domain.TestPage
import com.grayman.speakdaily.speech.Accent
import com.grayman.speakdaily.speech.VoiceOutput
import com.grayman.speakdaily.ui.AnswerTile
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.GameProgressBar
import com.grayman.speakdaily.ui.HeadphonesBadge
import com.grayman.speakdaily.ui.PlaybackWaveform
import com.grayman.speakdaily.ui.SecondaryButton
import com.grayman.speakdaily.ui.SoundWarning
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal val LETTERS = listOf("A", "B", "C", "D")

/** Who speaks a page: the main voice, and a second voice for answers or the other speaker. */
data class VoicePair(val main: Accent, val other: Accent) {
    val labelKo: String get() = if (main == other) main.labelKo else "${main.labelKo} · ${other.labelKo}"
}

/** One test in progress. Answers are keyed "page:question" so sets and single items share a map. */
class TestSession(
    val level: Level,
    val pages: List<TestPage>,
    val voices: List<VoicePair>,
    val practice: Boolean,
) {
    val answers = mutableStateMapOf<String, Int>()
    val totalQuestions = pages.sumOf { it.questionCount }

    /** The number shown for the first question of a page, counting from 1 across the whole test. */
    fun firstNumber(page: Int) = pages.take(page).sumOf { it.questionCount } + 1

    fun key(page: Int, question: Int) = "$page:$question"
}

/** Plays a page's audio in exam style. Each call cancels the previous one so voices never overlap. */
class PagePlayer(private val voice: VoiceOutput, private val rate: Float) {
    private var job: Job? = null
    var playing by mutableStateOf(false)
        private set
    var speakingOption by mutableIntStateOf(-1)
        private set
    var playedOnce by mutableStateOf(false)
        private set

    fun play(scope: kotlinx.coroutines.CoroutineScope, page: TestPage, pair: VoicePair) {
        job?.cancel()
        job = scope.launch {
            playing = true
            try {
                when (page) {
                    is TestPage.Response -> {
                        speakingOption = -1
                        voice.speak(page.item.prompt, pair.main, speaker = 0, rate = rate)
                        delay(PAUSE_MS)
                        page.item.options.forEachIndexed { index, option ->
                            speakingOption = index
                            voice.speak("${LETTERS[index]}. ${option.text}", pair.other, speaker = 1, rate = rate)
                            delay(PAUSE_MS / 2)
                        }
                    }

                    is TestPage.Set -> page.set.lines.forEach { line ->
                        val accent = if (line.speaker == 0) pair.main else pair.other
                        voice.speak(line.text, accent, speaker = line.speaker, rate = rate)
                    }
                }
            } finally {
                speakingOption = -1
                playing = false
                playedOnce = true
            }
        }
    }

    fun stop() {
        job?.cancel()
        voice.stop()
    }

    private companion object {
        const val PAUSE_MS = 600L
    }
}

@Composable
fun ListeningTestScreen(settings: ListeningTestSettings, onExit: () -> Unit, onRetry: () -> Unit) {
    val app = LocalContext.current.app
    val level = Level.fromCode(settings.levelCode)
    val session by produceState<TestSession?>(null, settings) {
        val pool = withContext(Dispatchers.IO) { app.listening.pool(level) }
        val seen = app.progress.recentlySeenListening(level)
        val accents = withTimeoutOrNull(ENGINE_WAIT_MS) { app.voice.accents.first { it.isNotEmpty() } }
            ?: listOf(Accent.US)
        val random = Random(System.nanoTime())
        val pages = ListeningTestBuilder.build(pool, if (settings.full) TestLength.FULL else TestLength.SHORT, seen, random)
        // Accents rotate per page, and a page's two voices differ when more than one accent is installed.
        val offset = random.nextInt(accents.size)
        val voices = pages.indices.map { i ->
            VoicePair(accents[(i + offset) % accents.size], accents[(i + offset + 1) % accents.size])
        }
        value = TestSession(level, pages, voices, settings.practice)
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            app.voice.stop()
        }
    }

    val current = session
    if (current == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Game.Lagoon)
        }
        return
    }
    TestRunner(current, onExit, onRetry)
}

@Composable
private fun TestRunner(session: TestSession, onExit: () -> Unit, onRetry: () -> Unit) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var showIntro by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = !finished) { confirmExit = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (finished) {
            ListeningResult(session, onExit, onRetry)
            return@Column
        }
        val page = session.pages[pageIndex]
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { confirmExit = true }) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "테스트 나가기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            val done = session.firstNumber(pageIndex) - 1
            GameProgressBar(done.toFloat() / session.totalQuestions, Modifier.weight(1f), color = Game.Xp)
            Text(
                "  ${session.firstNumber(pageIndex)}/${session.totalQuestions}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        fun next() {
            if (pageIndex + 1 >= session.pages.size) {
                finished = true
            } else {
                val nextPart = session.pages[pageIndex + 1].part
                showIntro = nextPart != page.part
                pageIndex++
            }
        }

        if (showIntro) {
            PartIntro(session, page) { showIntro = false }
        } else {
            // Keyed by page, so each page gets fresh playback state.
            androidx.compose.runtime.key(pageIndex) {
                QuestionPage(session, pageIndex, onNext = ::next)
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("테스트를 그만할까요?") },
            text = { Text("지금 나가면 이번 테스트 결과는 저장되지 않아요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 풀기") } },
            dismissButton = { TextButton(onClick = onExit) { Text("나가기") } },
        )
    }
}

@Composable
private fun PartIntro(session: TestSession, page: TestPage, onStart: () -> Unit) {
    val partNumber = page.part.ordinal + 1
    val count = session.pages.filter { it.part == page.part }.sumOf { it.questionCount }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        HeadphonesBadge(Modifier.size(88.dp))
        Text("Part $partNumber", style = MaterialTheme.typography.titleMedium, color = Game.Xp)
        Text(page.part.labelKo, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            page.part.guideKo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "${count}문제 · " + if (session.practice) "연습 모드: 다시 듣기 가능" else "실전 모드: 한 번만 재생",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChunkyButton("시작", onClick = onStart, color = Game.Xp, edge = Color(0xFF2F5BC4), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun QuestionPage(session: TestSession, pageIndex: Int, onNext: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val page = session.pages[pageIndex]
    val pair = session.voices[pageIndex]
    val player = remember { PagePlayer(app.voice, session.level.listeningRate()) }
    LaunchedEffect(Unit) { player.play(scope, page, pair) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val allAnswered = (0 until page.questionCount).all { session.key(pageIndex, it) in session.answers }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    page.part.labelKo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "억양: ${pair.labelKo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Game.LagoonEdge,
                    modifier = Modifier
                        .background(Game.LagoonSoft, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            SoundWarning()
            ListeningStatus(player.playing)
            when (page) {
                is TestPage.Response -> ResponseChoices(session, pageIndex, player)
                is TestPage.Set -> SetQuestions(session, pageIndex, page)
            }
            if (session.practice && player.playedOnce && !player.playing) {
                SecondaryButton("다시 듣기", icon = Icons.Rounded.Replay, onClick = { player.play(scope, page, pair) })
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            ChunkyButton(
                if (pageIndex + 1 == session.pages.size) "제출하기" else "다음",
                // The recording must finish first, as in the exam, so a fast tap cannot skip the audio.
                enabled = allAnswered && player.playedOnce && !player.playing,
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ListeningStatus(playing: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaybackWaveform(playing, Modifier.size(width = 72.dp, height = 44.dp))
        Text(
            if (playing) "잘 들어 보세요..." else "답을 고르세요",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResponseChoices(session: TestSession, pageIndex: Int, player: PagePlayer) {
    val key = session.key(pageIndex, 0)
    Text(
        "${session.firstNumber(pageIndex)}. 질문과 보기 모두 소리로만 나와요. 가장 알맞은 대답은?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LETTERS.take(3).forEachIndexed { index, letter ->
            val selected = session.answers[key] == index
            val speaking = player.speakingOption == index
            Box(
                Modifier
                    .weight(1f)
                    .size(96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        when {
                            selected -> Game.Xp
                            speaking -> Game.LagoonSoft
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        2.dp,
                        if (selected) Game.Xp else if (speaking) Game.Lagoon else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(18.dp),
                    )
                    .clickable(role = Role.RadioButton) { session.answers[key] = index },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SetQuestions(session: TestSession, pageIndex: Int, page: TestPage.Set) {
    val first = session.firstNumber(pageIndex)
    Text(
        "문제를 먼저 읽어 두면 듣기가 쉬워져요.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    page.set.questions.forEachIndexed { qIndex, question ->
        val key = session.key(pageIndex, qIndex)
        Text(
            "${first + qIndex}. ${question.q}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        question.options.forEachIndexed { oIndex, option ->
            AnswerTile(
                text = "${LETTERS[oIndex]}. $option",
                selected = session.answers[key] == oIndex,
                revealCorrect = false,
                revealWrong = false,
                enabled = true,
                onClick = { session.answers[key] = oIndex },
            )
        }
    }
}

private const val ENGINE_WAIT_MS = 6_000L
