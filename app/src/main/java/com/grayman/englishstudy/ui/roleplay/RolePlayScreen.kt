package com.grayman.englishstudy.ui.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.ai.GemmaTutor
import com.grayman.englishstudy.ai.RolePlay
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.data.Level
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.lesson.SpeakPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Line(val learner: Boolean, val text: String)

/** Learner turns after which the feedback button turns on. */
private const val MIN_TURNS = 2

/** A role-play with the on-device AI: chat on the left, microphone and coaching on the right. */
@Composable
fun RolePlayScreen(topicId: String, custom: String?, onExit: () -> Unit, onRestart: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val topic = remember(topicId, custom) { if (custom != null) RolePlay.customTopic(custom) else RolePlay.topic(topicId) }
    // A fresh situation, partner and seed every time the screen opens.
    val scenario = remember(topic) { RolePlay.scenario(topic) }
    val accents by app.voice.accents.collectAsState()
    val lines = remember { mutableStateListOf<Line>() }
    var chat by remember { mutableStateOf<GemmaTutor.TutorChat?>(null) }
    var level by remember { mutableStateOf(Level.B1) }
    var aiBusy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var speakingSeconds by remember { mutableStateOf(0.0) }
    var learnerTurns by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    fun speak(text: String) = scope.launch { app.voice.speak(text, app.voice.defaultAccent()) }

    /** Streams the AI's reply into a new line, then reads it aloud. */
    suspend fun reply(input: String) {
        val c = chat ?: return
        aiBusy = true
        lines += Line(false, "")
        val index = lines.lastIndex
        try {
            c.send(input).collect { lines[index] = Line(false, it.trim()) }
            speak(lines[index].text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "AI가 대답하지 못했어요: ${e.message}"
            if (lines[index].text.isEmpty()) lines.removeAt(index)
        } finally {
            aiBusy = false
        }
    }

    fun say(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || aiBusy || feedback != null) return
        hint = null
        learnerTurns++
        lines += Line(true, clean)
        scope.launch { reply(clean) }
    }

    LaunchedEffect(topicId) {
        level = app.progress.profile.first()?.let { Level.fromCode(it.levelCode) } ?: Level.B1
        val c = app.tutor.chat(RolePlay.systemInstruction(scenario, level))
        if (c == null) {
            error = app.tutor.state.value.error ?: "AI 모델이 아직 없어요. 말하기 탭에서 먼저 받아 주세요."
            aiBusy = false
            return@LaunchedEffect
        }
        chat = c
        reply(RolePlay.START)
    }
    LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    DisposableEffect(Unit) {
        onDispose {
            app.voice.stop()
            app.speech.cancel()
            chat?.let { c -> app.appScope.launch { c.close() } }
        }
    }
    BackHandler(enabled = feedback == null) { confirmExit = true }

    fun transcript() = lines.filter { it.text.isNotEmpty() }.map { it.learner to it.text }

    fun finish() {
        if (aiBusy) return
        app.voice.stop()
        feedback = "대화를 살펴보는 중이에요…"
        scope.launch {
            chat?.close()
            try {
                app.tutor.ask(RolePlay.feedbackPrompt(scenario, level, transcript())).collect { feedback = it }
                app.progress.addActivity(
                    ActivityKind.ROLEPLAY,
                    "역할극 · ${topic.titleKo}",
                    "${learnerTurns}번 대답 · 말한 시간 ${speakingSeconds.toInt()}초",
                    xp = 10 + learnerTurns * 5,
                    speakingSeconds = speakingSeconds.toInt(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feedback = "피드백을 만들지 못했어요: ${e.message}"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (feedback == null) confirmExit = true else onExit() }) {
                Icon(Icons.Rounded.Close, contentDescription = "나가기")
            }
            Column(Modifier.weight(1f)) {
                Text("역할극 · ${level.code.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(topic.titleKo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            val conversation: @Composable (Modifier) -> Unit = { m ->
                LazyColumn(m, state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(lines) { _, line -> Bubble(line) { speak(line.text) } }
                    if (aiBusy && (lines.isEmpty() || lines.last().text.isEmpty())) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.padding(4.dp), color = Game.Lagoon)
                                Text(
                                    if (chat == null) "AI를 불러오는 중이에요. 처음 한 번은 1분 정도 걸릴 수 있어요." else "AI가 생각하는 중…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            val side: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val fb = feedback
                    if (fb != null) {
                        FeedbackView(fb)
                        ChunkyButton("같은 주제, 다른 상황으로", onRestart, Modifier.fillMaxWidth())
                        SecondaryButton("말하기 탭으로", onExit, Modifier.fillMaxWidth())
                        return@Column
                    }
                    Column(
                        Modifier.fillMaxWidth().background(Game.LagoonSoft, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("목표", style = MaterialTheme.typography.labelLarge, color = Game.LagoonEdge)
                        Text(scenario.situation.goalKo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "상대: ${scenario.partnerName} · 매번 다른 상황과 돌발 상황이 나와요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Game.Bad) }
                    if (chat != null) {
                        if (typing) {
                            TypeBox(enabled = !aiBusy, onSend = ::say)
                            TextButton(onClick = { typing = false }) {
                                Icon(Icons.Rounded.Mic, null)
                                Text(" 말로 하기")
                            }
                        } else {
                            SpeakPanel(
                                wantRecording = false,
                                onResult = { result ->
                                    speakingSeconds += result.speechSeconds ?: 0.0
                                    say(result.text)
                                },
                                onSkip = { typing = true },
                            )
                        }
                        hint?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton(
                                "힌트",
                                icon = Icons.Rounded.Lightbulb,
                                onClick = {
                                    if (aiBusy) return@SecondaryButton
                                    hint = "생각 중…"
                                    scope.launch {
                                        try {
                                            app.tutor.ask(RolePlay.hintPrompt(scenario, level, transcript())).collect { hint = it.trim() }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            hint = "힌트를 만들지 못했어요: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ChunkyButton(
                                "끝내고 피드백",
                                onClick = ::finish,
                                enabled = learnerTurns >= MIN_TURNS && !aiBusy,
                                modifier = Modifier.weight(1.4f),
                            )
                        }
                        if (learnerTurns < MIN_TURNS) {
                            Text(
                                "${MIN_TURNS - learnerTurns}번 더 대답하면 피드백을 받을 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    conversation(Modifier.weight(0.58f).fillMaxHeight())
                    side(Modifier.weight(0.42f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    conversation(Modifier.weight(1f).fillMaxWidth())
                    side(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("역할극을 그만둘까요?") },
            text = { Text("피드백을 받지 않고 나가요. 대화 내용은 남지 않아요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false }) { Text("계속하기") } },
            dismissButton = { TextButton(onClick = onExit) { Text("나가기") } },
        )
    }
}

@Composable
private fun Bubble(line: Line, onSpeak: () -> Unit) {
    if (line.text.isEmpty()) return
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.learner) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (line.learner) Game.Lagoon else MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button, onClick = onSpeak)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!line.learner) Icon(Icons.AutoMirrored.Rounded.VolumeUp, "다시 듣기", tint = Game.Lagoon)
            Text(
                line.text,
                style = MaterialTheme.typography.titleMedium,
                color = if (line.learner) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypeBox(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isBlank()) return
        onSend(text)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        placeholder = { Text("영어로 입력하세요") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = { IconButton(onClick = ::send, enabled = enabled) { Icon(Icons.AutoMirrored.Rounded.Send, "보내기") } },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Shows the coach's "## 제목" sections as headings. */
@Composable
private fun FeedbackView(text: String) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        text.lines().filter { it.isNotBlank() }.forEach { raw ->
            val line = raw.replace("**", "").trim()
            if (line.startsWith("#")) {
                Text(
                    line.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Game.LagoonEdge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
