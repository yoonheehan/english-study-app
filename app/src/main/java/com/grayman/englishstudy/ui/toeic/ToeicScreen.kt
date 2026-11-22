package com.grayman.englishstudy.ui.toeic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.domain.Photo
import com.grayman.englishstudy.domain.ToeicDisplay
import com.grayman.englishstudy.domain.ToeicLevel
import com.grayman.englishstudy.domain.ToeicPart
import com.grayman.englishstudy.domain.ToeicQuestion
import com.grayman.englishstudy.domain.ToeicSpeaking
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.SecondaryButton
import kotlinx.coroutines.delay

private enum class Phase { LISTEN, PREP, SPEAK, WAIT }

/** TOEIC Speaking-style practice: a full mock test, or one part. Timers follow the real test. */
@Composable
fun ToeicScreen(part: ToeicPart?, level: ToeicLevel, onExit: () -> Unit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val questions = remember { ToeicSpeaking.build(part, level) }
    val answers = remember { mutableStateMapOf<Int, String>() }
    var index by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            app.speech.cancel()
            app.voice.stop()
        }
    }
    BackHandler(enabled = !finished) { confirmExit = true }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (finished) {
            ToeicResult(part, level, questions, answers, onExit, onAgain)
            return@Column
        }
        val q = questions[index]
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Rounded.Close, contentDescription = "그만두기") }
            Column(Modifier.widthIn(min = 200.dp)) {
                Text((if (part == null) "토익 스피킹 모의고사" else "토익 스피킹 · ${part.titleKo}") + " · ${level.titleKo}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Question ${q.number} · ${q.part.titleKo}", style = MaterialTheme.typography.titleMedium)
            }
            GameProgressBar(index.toFloat() / questions.size, Modifier.weight(1f), color = Game.Xp)
            Text(" ${index + 1}/${questions.size}", style = MaterialTheme.typography.labelLarge)
        }
        if (!hasMic) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("말하기 연습에는 마이크가 필요해요.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                ChunkyButton("마이크 켜기", { permission.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.width(240.dp))
            }
            return@Column
        }
        key(index) {
            QuestionRunner(q) { spoken ->
                answers[index] = spoken
                if (index == questions.lastIndex) {
                    finished = true
                } else {
                    index++
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("연습을 그만둘까요?") },
            text = { Text("지금까지 답한 내용은 저장되지 않아요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false }) { Text("계속하기") } },
            dismissButton = { TextButton(onClick = onExit) { Text("그만두기") } },
        )
    }
}

@Composable
private fun QuestionRunner(q: ToeicQuestion, onAnswered: (String) -> Unit) {
    val app = LocalContext.current.app
    val speech by app.speech.state.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(if (q.spokenPrompt != null) Phase.LISTEN else Phase.PREP) }
    var remaining by remember { mutableIntStateOf(q.prepSeconds) }
    var heard by remember { mutableStateOf("") }

    fun startSpeaking() {
        phase = Phase.SPEAK
        remaining = q.speakSeconds
        app.speech.listen(
            wantRecording = false,
            onResult = { onAnswered(it.text) },
            onFailure = { onAnswered("") },
        )
    }

    LaunchedEffect(phase) {
        when (phase) {
            Phase.LISTEN -> {
                q.spokenPrompt?.let { app.voice.speak(it, app.voice.defaultAccent(), rate = 0.95f) }
                remaining = q.prepSeconds
                phase = Phase.PREP
            }
            Phase.PREP -> {
                while (remaining > 0) {
                    delay(1000)
                    remaining--
                }
                startSpeaking()
            }
            Phase.SPEAK -> {
                while (remaining > 0) {
                    delay(1000)
                    remaining--
                }
                phase = Phase.WAIT
                app.speech.finishSpeaking()
            }
            Phase.WAIT -> Unit
        }
    }
    LaunchedEffect(speech.partial) { if (speech.partial.isNotBlank()) heard = speech.partial }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Side by side in landscape; in portrait the question (photo, schedule) gets the full width on top.
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        val task: @Composable (Modifier) -> Unit = { m -> TaskView(q, m) }
        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(q.part.guideKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                q.questionText?.takeIf { q.part == ToeicPart.RESPOND || q.part == ToeicPart.PICTURE }?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (q.part == ToeicPart.INFO && phase != Phase.LISTEN && phase != Phase.PREP) {
                    Text("질문은 소리로만 나와요. 다시 들으려면 아래를 눌러요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.fillMaxWidth().background(
                        when (phase) { Phase.SPEAK -> Game.BadSoft; Phase.PREP -> Game.LagoonSoft; else -> MaterialTheme.colorScheme.surfaceVariant },
                        RoundedCornerShape(20.dp),
                    ).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (phase) {
                            Phase.LISTEN -> "질문을 들어 보세요"
                            Phase.PREP -> "준비 시간"
                            Phase.SPEAK -> "지금 말하세요"
                            Phase.WAIT -> "답변을 정리하는 중…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (phase == Phase.SPEAK) Game.Bad else Game.LagoonEdge,
                    )
                    if (phase == Phase.PREP || phase == Phase.SPEAK) {
                        Text("$remaining", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (phase == Phase.SPEAK) Game.Bad else Game.LagoonEdge)
                        Text("초", style = MaterialTheme.typography.labelLarge)
                    }
                    if (phase == Phase.SPEAK) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Mic, null, tint = Game.Bad)
                            GameProgressBar(speech.level, Modifier.width(160.dp), color = Game.Bad, height = 8.dp)
                        }
                    }
                }
                if (heard.isNotBlank() && phase != Phase.PREP) {
                    Text("들린 말: $heard", style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (q.spokenPrompt != null && phase == Phase.PREP) {
                        SecondaryButton("질문 다시 듣기", { app.voice.stop(); phase = Phase.LISTEN }, Modifier.weight(1f))
                    }
                    when (phase) {
                        Phase.PREP -> ChunkyButton("준비 끝, 말하기", ::startSpeaking, Modifier.weight(1f))
                        Phase.SPEAK -> ChunkyButton("다 말했어요", { phase = Phase.WAIT; app.speech.finishSpeaking() }, Modifier.weight(1f))
                        else -> Unit
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                task(Modifier.weight(0.58f).fillMaxHeight())
                panel(Modifier.weight(0.42f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                task(Modifier.fillMaxWidth().weight(1.2f))
                panel(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun TaskView(q: ToeicQuestion, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.background(MaterialTheme.colorScheme.surface, shape).border(2.dp, MaterialTheme.colorScheme.outline, shape).padding(24.dp)) {
        when (val d = q.display) {
            is ToeicDisplay.Text -> Text(
                d.text,
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = 38.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            is ToeicDisplay.Picture -> PictureView(d.photo, Modifier.fillMaxSize())
            is ToeicDisplay.Intro -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(d.text, style = MaterialTheme.typography.titleLarge, lineHeight = 32.sp)
            }
            is ToeicDisplay.Schedule -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                d.rows.forEach { (time, item) ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Text(time, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(220.dp))
                        Text(item, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
                Text(d.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The photo for describe-a-picture, loaded from assets, with its credit. */
@Composable
fun PictureView(photo: Photo, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photo.file) {
        runCatching { context.assets.open(photo.assetPath).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = "묘사할 사진",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Text("사진을 불러오지 못했어요.", color = Game.Bad)
        }
        Text("사진: ${photo.credit} · CC0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToeicResult(part: ToeicPart?, level: ToeicLevel, questions: List<ToeicQuestion>, answers: SnapshotStateMap<Int, String>, onExit: () -> Unit, onAgain: () -> Unit) {
    val app = LocalContext.current.app
    val tutor by app.tutor.state.collectAsState()
    val quick = remember { questions.mapIndexed { i, q -> ToeicSpeaking.estimate(q, answers[i].orEmpty()) } }
    // The AI scores every answer on its own, one after another, as soon as the model is available.
    val aiScores = remember { mutableStateMapOf<Int, Int>() }
    val aiTexts = remember { mutableStateMapOf<Int, String>() }
    var aiIndex by remember { mutableIntStateOf(-1) }
    var aiDone by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    LaunchedEffect(tutor.downloaded) {
        if (!tutor.downloaded || aiDone) return@LaunchedEffect
        for ((i, q) in questions.withIndex()) {
            aiIndex = i
            val spoken = answers[i].orEmpty()
            if (spoken.isBlank()) {
                aiScores[i] = 0
                continue
            }
            try {
                app.tutor.ask(ToeicSpeaking.reviewPrompt(q, spoken)).collect { aiTexts[i] = it }
                aiScores[i] = ToeicSpeaking.parseScore(aiTexts[i].orEmpty(), q.part.maxScore) ?: quick[i]
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                aiTexts[i] = "AI 채점에 실패했어요: ${e.message}"
                aiScores[i] = quick[i]
            }
        }
        aiIndex = -1
        aiDone = true
    }

    val scores = questions.indices.map { aiScores[it] ?: quick[it] }
    val total = scores.sum()
    val max = questions.sumOf { it.part.maxScore }
    val scaled = ToeicSpeaking.estimateScaled(total, max)
    val (grade, gradeNote) = ToeicSpeaking.grade(scaled)
    val finalReady = aiDone || !tutor.downloaded
    LaunchedEffect(finalReady) {
        if (!finalReady || logged) return@LaunchedEffect
        logged = true
        val answered = answers.values.count { it.isNotBlank() }
        app.progress.addActivity(
            ActivityKind.SPEAKING,
            (if (part == null) "토익 스피킹 모의고사" else "토익 스피킹 · ${part.titleKo}") + " · ${level.titleKo}",
            "${questions.size}문항 중 ${answered}개 답변 · ${if (aiDone) "AI 채점" else "참고"} $total/$max · 예상 ${scaled}점 ($grade)",
            xp = 5 + answered * 5,
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFFDDE7FD), RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (part == null) "예상 점수" else "이 파트 기준 예상 수준", style = MaterialTheme.typography.labelLarge, color = Game.Xp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("약 ${scaled}점", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Game.Xp)
                Text("/ 200 · $grade", style = MaterialTheme.typography.titleLarge, color = Game.Xp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(gradeNote, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !tutor.downloaded -> "지금은 말한 양과 핵심어로 낸 대략적인 점수예요. 말하기 탭에서 AI 모델을 받으면 다음부터 AI가 토익 기준으로 자동 채점해요."
                    !aiDone -> "AI가 채점하는 중이에요 (${(aiIndex + 1).coerceAtLeast(1)}/${questions.size}). 끝나면 점수가 바뀔 수 있어요."
                    else -> "AI가 문항별로 토익 채점 기준(0-3점, 의견 0-5점)으로 매긴 점수를 200점 만점으로 환산했어요. 공식 점수가 아니라 '대략 이 정도' 참고용이에요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (level) {
                    ToeicLevel.BASIC -> "기초 문제로 본 점수라 실제 시험보다 조금 높게 나올 수 있어요. 160점 이상이 나오면 중급으로 올려 보세요."
                    ToeicLevel.MIDDLE -> "실제 시험과 비슷한 난이도예요. 170점 이상이 꾸준히 나오면 고급에 도전해 보세요."
                    ToeicLevel.ADVANCED -> "실제 시험보다 어려운 문제라, 같은 실력이면 실제 점수가 이보다 높게 나올 수 있어요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("문항 점수 합계 $total / $max", style = MaterialTheme.typography.labelLarge)
        }
        questions.forEachIndexed { i, q ->
            var open by remember { mutableStateOf(false) }
            val spoken = answers[i].orEmpty()
            val ai = aiTexts[i]
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { open = !open }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Q${q.number} · ${q.part.titleKo}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            aiScores[i] != null -> "AI ${scores[i]} / ${q.part.maxScore}"
                            aiIndex == i -> "AI 채점 중…"
                            else -> "${scores[i]} / ${q.part.maxScore}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Game.Xp,
                    )
                }
                q.questionText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("내 답: " + spoken.ifBlank { "(인식된 말이 없어요)" }, style = MaterialTheme.typography.bodyLarge)
                if (ai != null) {
                    Text(
                        ai,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                    )
                }
                if (open) {
                    if (q.part == ToeicPart.PICTURE) PictureView((q.display as ToeicDisplay.Picture).photo, Modifier.fillMaxWidth().height(320.dp))
                    if (q.modelAnswer.isNotBlank()) {
                        Text(
                            (if (q.part == ToeicPart.READ_ALOUD) "원문: " else "예시 답변: ") + q.modelAnswer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Game.LagoonEdge,
                        )
                    }
                } else {
                    Text("눌러서 ${if (q.part == ToeicPart.PICTURE) "사진과 " else ""}예시 답변 보기", style = MaterialTheme.typography.labelMedium, color = Game.LagoonEdge)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("스피킹 탭으로", onExit, Modifier.weight(1f))
            ChunkyButton("새 문제로 다시", onAgain, Modifier.weight(1f))
        }
    }
}

/** The TOEIC section of the speaking tab: a level, then the mock test or one part. */
@Composable
fun ToeicHome(onStart: (ToeicPart?, ToeicLevel) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("toeic", android.content.Context.MODE_PRIVATE) }
    var level by remember {
        mutableStateOf(prefs.getString("level", null)?.let { name -> ToeicLevel.entries.firstOrNull { it.name == name } } ?: ToeicLevel.MIDDLE)
    }
    fun choose(l: ToeicLevel) {
        level = l
        prefs.edit().putString("level", l.name).apply()
    }
    val times = mapOf(
        ToeicPart.READ_ALOUD to "Q1-2 · 준비 45초 · 답변 45초",
        ToeicPart.PICTURE to "Q3-4 · 준비 45초 · 답변 30초",
        ToeicPart.RESPOND to "Q5-7 · 준비 3초 · 답변 15/15/30초",
        ToeicPart.INFO to "Q8-10 · 읽기 45초 · 답변 15/15/30초",
        ToeicPart.OPINION to "Q11 · 준비 45초 · 답변 60초",
    )
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("난이도", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToeicLevel.entries.forEach { l ->
                val selected = l == level
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFFDDE7FD) else MaterialTheme.colorScheme.surface)
                        .border(2.dp, if (selected) Game.Xp else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { choose(l) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(l.titleKo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (selected) Game.Xp else MaterialTheme.colorScheme.onSurface)
                    Text(l.targetKo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(level.guideKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().background(Color(0xFFDDE7FD), RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("토익 스피킹 모의고사 · ${level.titleKo}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Game.Xp)
            Text(
                "실제 시험과 같은 순서와 시간으로 11문항을 풀어요 (약 20분). 끝나면 AI가 문항별로 채점해 예상 점수와 첨삭을 보여 줘요.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "이 난이도 문제: 읽기 지문 ${ToeicSpeaking.poolSize(ToeicPart.READ_ALOUD, level)}개 · 사진 ${ToeicSpeaking.poolSize(ToeicPart.PICTURE, level)}장 · " +
                    "질문 세트 ${ToeicSpeaking.poolSize(ToeicPart.RESPOND, level)}개 · 일정표 ${ToeicSpeaking.poolSize(ToeicPart.INFO, level)}개 · 의견 주제 ${ToeicSpeaking.poolSize(ToeicPart.OPINION, level)}개",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChunkyButton("모의고사 시작", { onStart(null, level) }, Modifier.width(280.dp), color = Game.Xp, edge = Color(0xFF2F5BC4))
        }
        Text("파트별 연습", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val columns = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 2 else 3
        ToeicPart.entries.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { onStart(p, level) }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(p.titleKo, style = MaterialTheme.typography.titleMedium, color = Game.Xp)
                        Text(times.getValue(p), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(p.guideKo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
