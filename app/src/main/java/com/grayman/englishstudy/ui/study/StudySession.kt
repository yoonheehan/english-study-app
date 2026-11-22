package com.grayman.englishstudy.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.grayman.englishstudy.ai.Explain
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.ui.ai.AiExplainBox
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.domain.AnswerMatcher
import com.grayman.englishstudy.domain.AnswerMethod
import com.grayman.englishstudy.domain.QuizQuestion
import com.grayman.englishstudy.domain.StudyDirection
import com.grayman.englishstudy.domain.StudySettings
import com.grayman.englishstudy.domain.StudySource
import com.grayman.englishstudy.domain.WordQuiz
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Confetti
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.words.partOfSpeechKo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the pen must rest before the pad is read on its own. */
private const val AUTO_READ_MS = 700L

/** One answer and how it was graded, shown under the question. */
private data class Graded(val correct: Boolean, val mine: String, val overridden: Boolean = false)

/** The word test: questions on the left, the answer on the right (stacked on a narrow screen). */
@Composable
fun StudySession(settings: StudySettings, onExit: () -> Unit, onAgain: () -> Unit) {
    val app = LocalContext.current.app
    val words by produceState<List<WordEntity>?>(null, settings) {
        value = app.words.studyWords(settings.source, settings.count)
    }
    val list = words
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Game.Lagoon) }
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${settings.source.labelKo}가 지금은 없어요.", style = MaterialTheme.typography.titleLarge)
                ChunkyButton("돌아가기", onExit, Modifier.width(240.dp))
            }
        }
        else -> {
            // Memorize the picked words first, then take the test on the same words.
            var memorizing by remember(list) { mutableStateOf(true) }
            if (memorizing) MemorizeStep(settings, list, onExit = onExit, onStart = { memorizing = false })
            else Quiz(settings, list, onExit, onAgain)
        }
    }
}

@Composable
private fun Quiz(settings: StudySettings, words: List<WordEntity>, onExit: () -> Unit, onAgain: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val byId = remember(words) { words.associateBy { it.id } }
    val quiz = remember(words) { WordQuiz(words.map { it.id }, settings.direction) }
    // WordQuiz is plain Kotlin; this counter tells Compose to read it again after each step.
    var tick by remember { mutableIntStateOf(0) }
    var graded by remember { mutableStateOf<Graded?>(null) }
    var finished by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var roundBanner by remember { mutableStateOf(false) }

    val english = remember { InkReader("en-US") }
    val korean = remember { InkReader("ko-KR") }
    DisposableEffect(Unit) {
        onDispose {
            english.close()
            korean.close()
        }
    }
    if (settings.method == AnswerMethod.HANDWRITING) {
        LaunchedEffect(Unit) {
            launch { english.prepare() }
            launch { korean.prepare() }
        }
    }

    fun finish() {
        finished = true
        scope.launch {
            app.words.saveStudy(settings.source, words.map { it.id }, quiz.failedIds)
            val firstTry = words.size - quiz.failedIds.size
            app.progress.addActivity(
                ActivityKind.WORDS,
                "단어 외우기 · ${settings.source.labelKo} ${words.size}개",
                "${settings.direction.labelKo} · 한 번에 맞힘 ${firstTry}개 · 틀린 답 ${quiz.wrongAnswers}회",
                xp = words.size * 2 + firstTry,
            )
            saved = true
        }
    }

    fun next() {
        graded = null
        val newRound = quiz.next()
        tick++
        if (quiz.finished) finish() else if (newRound) roundBanner = true
    }

    BackHandler(enabled = !finished) { confirmExit = true }

    if (finished) {
        tick.let { }
        StudyDone(settings, words.size, quiz, saved, onExit, onAgain)
        return
    }

    tick.let { }
    val question = quiz.current ?: return
    val word = byId.getValue(question.wordId)
    val reader = if (question.askEnglish) english else korean

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(
            settings = settings,
            rounds = quiz.rounds,
            round = quiz.round,
            remaining = quiz.remaining,
            roundSize = quiz.roundSize,
            question = question,
            onClose = { confirmExit = true },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            // Keyed by question, so every question starts with a clean pad and field.
            key(tick, question) {
                val prompt: @Composable (Modifier) -> Unit = { m ->
                    PromptCard(word, question, graded, m)
                }
                val answer: @Composable (Modifier) -> Unit = { m ->
                    AnswerArea(
                        method = settings.method,
                        question = question,
                        word = word,
                        reader = reader,
                        graded = graded,
                        onGrade = { mine ->
                            val correct = if (question.askEnglish) AnswerMatcher.matchesEnglish(mine, word.word)
                            else AnswerMatcher.matchesMeaning(mine, word.meaning)
                            quiz.answer(correct)
                            graded = Graded(correct, mine)
                        },
                        onSelfGrade = { knew ->
                            quiz.answer(knew)
                            graded = Graded(knew, "")
                        },
                        onOverride = {
                            quiz.overrideLastWrong()
                            graded = graded?.copy(correct = true, overridden = true)
                        },
                        onNext = ::next,
                        lastQuestion = quiz.remaining == 1 && graded?.correct == true,
                        modifier = m,
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        prompt(Modifier.weight(0.42f).fillMaxHeight())
                        answer(Modifier.weight(0.58f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        prompt(Modifier.fillMaxWidth())
                        answer(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    if (roundBanner) {
        AlertDialog(
            onDismissRequest = { roundBanner = false },
            title = { Text("1차 통과!") },
            text = { Text("이제 한글 뜻을 보고 영어 단어를 써 보세요.") },
            confirmButton = { TextButton(onClick = { roundBanner = false }) { Text("2차 시작") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("시험을 그만둘까요?") },
            text = { Text("끝까지 마쳐야 외운 단어로 저장돼요. 지금 나가면 이번 시험 결과는 남지 않아요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 풀기") } },
            dismissButton = { TextButton(onClick = onExit) { Text("그만두기") } },
        )
    }
}

@Composable
private fun TopBar(
    settings: StudySettings,
    rounds: Int,
    round: Int,
    remaining: Int,
    roundSize: Int,
    question: QuizQuestion,
    onClose: () -> Unit,
) {
    // Plain numbers rather than the quiz object, so Compose redraws the bar after every answer.
    val roundLabel = buildString {
        if (rounds == 2) append("${round}차 · ")
        append(if (question.askEnglish) "뜻을 보고 영어 쓰기" else "영어를 보고 한글 뜻 쓰기")
    }
    val done = (roundSize - remaining).coerceAtLeast(0)
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
    if (compact) {
        // Phone: the progress bar gets its own line instead of being squeezed beside the title.
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "시험 그만두기") }
                Column(Modifier.weight(1f)) {
                    Text("${settings.source.labelKo} 시험 · ${remaining}문제 남음", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(roundLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            GameProgressBar(done.toFloat() / roundSize.coerceAtLeast(1), Modifier.fillMaxWidth().padding(start = 12.dp), color = Game.Good)
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "시험 그만두기") }
        Column(Modifier.widthIn(min = 160.dp)) {
            Text("${settings.source.labelKo} 시험", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(roundLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        GameProgressBar(done.toFloat() / roundSize.coerceAtLeast(1), Modifier.weight(1f), color = Game.Good)
        Text("${remaining}문제 남음", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PromptCard(word: WordEntity, question: QuizQuestion, graded: Graded?, modifier: Modifier) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val accents by app.voice.accents.collectAsState()
    fun speak(text: String) = scope.launch { app.voice.speak(text, app.voice.defaultAccent()) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (word.partOfSpeech.isNotEmpty()) {
            Text(partOfSpeechKo(word.partOfSpeech), style = MaterialTheme.typography.labelLarge, color = Game.LagoonEdge)
        }
        if (question.askEnglish) {
            Text(word.meaning, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                IconButton(onClick = { speak(word.word) }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "${word.word} 발음 듣기", tint = Game.Lagoon)
                }
            }
            if (word.pronunciation.isNotEmpty()) {
                Text(word.pronunciation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (graded != null) {
            val good = graded.correct
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (good) Game.GoodSoft else Game.BadSoft)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val ink = if (good) Game.OnGoodSoft else Game.OnBadSoft
                Text(
                    when {
                        graded.overridden -> "정답으로 처리했어요"
                        good -> "정답이에요!"
                        graded.mine.isEmpty() -> "괜찮아요. 이 문제는 다시 나와요."
                        else -> "아쉬워요. 이 문제는 다시 나와요."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                )
                if (graded.mine.isNotEmpty()) Text("내 답: ${graded.mine}", style = MaterialTheme.typography.bodyLarge, color = ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "정답: " + if (question.askEnglish) word.word else word.meaning,
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (question.askEnglish) {
                        IconButton(onClick = { speak(word.word) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "${word.word} 발음 듣기", tint = ink)
                        }
                    }
                }
            }
            if (!graded.correct) {
                AiExplainBox(
                    prompt = {
                        Explain.word(word.word, word.meaning, question.askEnglish, graded.mine, word.example1.ifEmpty { word.sourceSentence })
                    },
                    label = "AI 해설 · 왜 틀렸을까?",
                )
            }
            val example = word.example1.ifEmpty { word.sourceSentence }
            if (example.isNotEmpty()) {
                Column(Modifier.clickable { speak(example) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("예문", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(example, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (word.example1.isNotEmpty() && word.example1Meaning.isNotEmpty()) {
                        Text(word.example1Meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerArea(
    method: AnswerMethod,
    question: QuizQuestion,
    word: WordEntity,
    reader: InkReader,
    graded: Graded?,
    onGrade: (String) -> Unit,
    onSelfGrade: (Boolean) -> Unit,
    onOverride: () -> Unit,
    onNext: () -> Unit,
    lastQuestion: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (method) {
            AnswerMethod.SELF -> SelfGrade(question, word, graded, onSelfGrade, Modifier.weight(1f))
            AnswerMethod.KEYBOARD -> KeyboardAnswer(question, graded, onGrade, Modifier.weight(1f))
            AnswerMethod.HANDWRITING -> HandwritingAnswer(question, reader, graded, onGrade, Modifier.weight(1f))
        }
        if (graded != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!graded.correct && graded.mine.isNotEmpty()) {
                    SecondaryButton("제가 맞게 썼어요 · 정답 처리", onOverride, Modifier.weight(1f))
                }
                ChunkyButton(
                    text = if (lastQuestion) "다음" else "다음 문제",
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    color = if (graded.correct) Game.Good else Game.Lagoon,
                    edge = if (graded.correct) Game.GoodEdge else Game.LagoonEdge,
                )
            }
        }
    }
}

@Composable
private fun SelfGrade(question: QuizQuestion, word: WordEntity, graded: Graded?, onSelfGrade: (Boolean) -> Unit, modifier: Modifier) {
    var revealed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!revealed) {
            Text(
                if (question.askEnglish) "영어 단어를 떠올려 보세요" else "한글 뜻을 떠올려 보세요",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChunkyButton("정답 보기", { revealed = true }, Modifier.width(280.dp))
        } else {
            Text("정답", style = MaterialTheme.typography.labelLarge, color = Game.LagoonEdge)
            Text(
                if (question.askEnglish) word.word else word.meaning,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (graded == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChunkyButton("몰랐어요", { onSelfGrade(false) }, Modifier.weight(1f), color = Game.Bad, edge = Game.CoralEdge)
                    ChunkyButton("알았어요", { onSelfGrade(true) }, Modifier.weight(1f), color = Game.Good, edge = Game.GoodEdge)
                }
            }
        }
    }
}

@Composable
private fun KeyboardAnswer(question: QuizQuestion, graded: Graded?, onGrade: (String) -> Unit, modifier: Modifier) {
    val field = rememberTextFieldState()
    val text = field.text.toString()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        if (graded != null) return
        keyboard?.hide()
        onGrade(text.trim())
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            state = field,
            enabled = graded == null,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.headlineSmall,
            placeholder = { Text(if (question.askEnglish) "영어 단어를 입력하세요" else "뜻을 입력하세요") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (graded == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("모르겠어요", { onGrade("") }, Modifier.weight(1f))
                ChunkyButton("정답 확인", ::submit, Modifier.weight(1f), enabled = text.isNotBlank())
            }
        }
    }
}

@Composable
private fun HandwritingAnswer(question: QuizQuestion, reader: InkReader, graded: Graded?, onGrade: (String) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val state by reader.state.collectAsState()
    // TextFieldState (not a String value): with the Samsung keyboard and Korean composition, the
    // String version kept moving the cursor back to the end, so tapping inside the text did nothing.
    val answerField = rememberTextFieldState()
    val answer = answerField.text.toString()
    var lastSetByApp by remember { mutableStateOf("") }
    fun setAnswer(text: String) {
        lastSetByApp = text
        answerField.setTextAndPlaceCursorAtEnd(text)
    }
    var candidates by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var inkVersion by remember { mutableIntStateOf(0) }
    var eraser by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    // Once the learner fixes the text by hand, new strokes no longer overwrite it.
    var editedByHand by remember { mutableStateOf(false) }
    var pad by remember { mutableStateOf<HandwritingPad?>(null) }
    val inkColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val lineColor = MaterialTheme.colorScheme.outline.toArgb()
    val language = if (question.askEnglish) "영어" else "한글"

    suspend fun read(): String? {
        val p = pad ?: return null
        if (!p.hasInk) return null
        reading = true
        status = "읽는 중…"
        val result = runCatching { reader.read(p.ink()) }.getOrDefault(emptyList())
        reading = false
        if (result.isEmpty()) {
            status = "글씨를 읽지 못했어요. 조금 더 크게 써 보세요."
            return null
        }
        candidates = result
        if (!editedByHand) setAnswer(result.first())
        status = if (result.size > 1) "다르게 읽혔다면 아래 후보를 누르거나 칸을 눌러 고치세요." else "다르게 읽혔다면 칸을 눌러 고치세요."
        return answerField.text.toString()
    }

    LaunchedEffect(inkVersion) {
        if (inkVersion == 0 || graded != null) return@LaunchedEffect
        if (pad?.hasInk != true) {
            if (!editedByHand) setAnswer("")
            candidates = emptyList()
            status = null
            return@LaunchedEffect
        }
        if (state != InkReader.State.READY) return@LaunchedEffect
        delay(AUTO_READ_MS)
        read()
    }
    LaunchedEffect(graded) { pad?.isEnabled = graded == null }
    LaunchedEffect(answerField) {
        snapshotFlow { answerField.text.toString() }.collect { text -> if (text != lastSetByApp) editedByHand = text.isNotEmpty() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, if (eraser) Game.Coral else MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        ) {
            AndroidView(
                factory = { context -> HandwritingPad(context, inkColor, lineColor) { inkVersion++ }.also { pad = it } },
                update = { it.eraserMode = eraser },
                modifier = Modifier.fillMaxSize(),
            )
            if (inkVersion == 0) {
                Text(
                    "여기에 ${if (question.askEnglish) "영어 단어를" else "한글 뜻을 (~ 표시는 빼고)"} 크게 써 보세요",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
        val statusText = when (state) {
            InkReader.State.PREPARING -> "$language 손글씨 모델 받는 중…"
            InkReader.State.FAILED -> "$language 손글씨 모델을 받지 못했어요. 칸에 키보드로 써도 돼요."
            InkReader.State.READY -> status ?: "쓰고 잠깐 멈추면 자동으로 읽어요."
        }
        val statusColor = if (state == InkReader.State.FAILED) Game.Bad else MaterialTheme.colorScheme.onSurfaceVariant
        val tools: @Composable () -> Unit = {
            SecondaryButton(if (compact) "한 획" else "한 획 지우기", { pad?.undo() }, icon = Icons.AutoMirrored.Rounded.Undo)
            ToggleButton(if (eraser) "지우개 켜짐" else "지우개", eraser, Icons.Rounded.Edit) { eraser = !eraser }
            SecondaryButton(if (compact) "모두" else "모두 지우기", { pad?.clear(); editedByHand = false }, icon = Icons.Rounded.DeleteSweep)
        }
        if (compact) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                tools()
                if (state == InkReader.State.FAILED) {
                    IconButton(onClick = { scope.launch { reader.prepare() } }) { Icon(Icons.Rounded.Replay, contentDescription = "손글씨 모델 다시 받기") }
                }
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            tools()
            Spacer(Modifier.weight(1f))
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor, textAlign = TextAlign.End, modifier = Modifier.weight(2f))
            if (state == InkReader.State.FAILED) {
                IconButton(onClick = { scope.launch { reader.prepare() } }) { Icon(Icons.Rounded.Replay, contentDescription = "손글씨 모델 다시 받기") }
            }
        }

        if (candidates.size > 1 && graded == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("후보", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                candidates.forEach { c ->
                    val selected = c == answer
                    Text(
                        c,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.Button) {
                                setAnswer(c)
                                editedByHand = true
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (graded == null) {
            val answerField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(
                    state = answerField,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.titleLarge,
                    label = { Text("읽힌 답 · 틀린 글자는 눌러서 고치세요") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = m,
                )
            }
            val checkButton: @Composable (Modifier) -> Unit = { m ->
                ChunkyButton(
                    text = if (reading) "읽는 중…" else "정답 확인",
                    enabled = !reading && (answer.isNotBlank() || pad?.hasInk == true),
                    onClick = {
                        scope.launch {
                            val final = answer.trim().ifEmpty { read()?.trim().orEmpty() }
                            if (final.isNotEmpty()) onGrade(final)
                        }
                    },
                    modifier = m,
                )
            }
            if (compact) {
                answerField(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("모르겠어요", { onGrade("") }, Modifier.weight(0.4f))
                    checkButton(Modifier.weight(0.6f))
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                answerField(Modifier.weight(1.4f))
                SecondaryButton("모르겠어요", { onGrade("") }, Modifier.weight(0.6f))
                checkButton(Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun ToggleButton(text: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(if (on) Game.Coral else Color.Transparent)
            .border(2.dp, if (on) Game.CoralEdge else MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StudyDone(settings: StudySettings, total: Int, quiz: WordQuiz, saved: Boolean, onExit: () -> Unit, onAgain: () -> Unit) {
    val firstTry = total - quiz.failedIds.size
    val detail = when (settings.source) {
        StudySource.WRONG -> "한 번에 맞힌 ${firstTry}개는 틀린 횟수를 하나 줄였어요."
        StudySource.DUE -> "한 번에 맞힌 ${firstTry}개는 다음 복습이 더 뒤로 미뤄졌어요. 틀렸던 단어는 내일 다시 나와요."
        else -> "${total}개를 외운 단어로 저장했어요. 내일부터 복습에 나와요." +
            if (settings.direction != StudyDirection.BOTH) " (${settings.direction.labelKo}만 봤어요)" else ""
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (quiz.failedIds.isEmpty()) Confetti()
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 640.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                when (settings.source) {
                    StudySource.DUE -> "복습을 마쳤어요!"
                    StudySource.WRONG -> "오답 시험을 마쳤어요!"
                    else -> "시험을 통과했어요!"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("한 번에 맞힘", "${firstTry}개", Game.Good)
                Stat("틀린 답", "${quiz.wrongAnswers}회", Game.Bad)
            }
            Text(detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!saved) Text("저장하는 중…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("단어장으로", onExit, Modifier.weight(1f))
                ChunkyButton("${settings.count}개 더 하기", onAgain, Modifier.weight(1f), enabled = saved, icon = Icons.Rounded.Replay)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Below this width (a phone held upright) controls stack instead of sharing one row. */
private const val COMPACT_WIDTH_DP = 600
