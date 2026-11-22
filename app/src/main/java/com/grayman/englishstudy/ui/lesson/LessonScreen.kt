package com.grayman.englishstudy.ui.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.Lesson
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.SoundWarning
import kotlinx.coroutines.flow.first

enum class LessonStep(val labelKo: String) {
    REVIEW("복습"),
    LISTEN("집중 듣기"),
    SHADOW("쉐도잉"),
    RESPOND("대답 연습"),
    SUMMARY("결과"),
}

/** Totals earned in one lesson. Plain state because the lesson is one uninterrupted screen. */
class LessonScore {
    var xp by mutableIntStateOf(0)
    var speakingSeconds by mutableFloatStateOf(0f)
    var bestResponse by mutableIntStateOf(0)
}

@Composable
fun LessonScreen(lessonId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val lesson = remember(lessonId) { app.content.lesson(lessonId) }
    val score = remember(lessonId) { LessonScore() }
    var step by remember { mutableStateOf(LessonStep.REVIEW) }
    var stepProgress by remember { mutableFloatStateOf(0f) }
    var confirmExit by remember { mutableStateOf(false) }

    var accent by remember { mutableStateOf(Accent.US) }
    val accents by app.voice.accents.collectAsState()
    LaunchedEffect(Unit) {
        app.voice.accents.first { it.isNotEmpty() }
        accent = app.voice.accentForDay(app.progress.today())
    }

    // A learner reading or listening does not touch the screen; it must not sleep mid-lesson.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            app.voice.stop()
        }
    }

    BackHandler(enabled = step != LessonStep.SUMMARY) { confirmExit = true }

    fun next(from: LessonStep) {
        stepProgress = 0f
        step = LessonStep.entries[from.ordinal + 1]
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (step != LessonStep.SUMMARY) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { confirmExit = true }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "레슨 나가기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                val total = (step.ordinal + stepProgress) / (LessonStep.entries.size - 1)
                GameProgressBar(total, Modifier.weight(1f))
            }
        }

        if (step != LessonStep.SUMMARY) {
            SoundWarning(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                LessonStep.REVIEW -> ReviewStep(
                    lesson = lesson,
                    accent = accent,
                    score = score,
                    onProgress = { stepProgress = it },
                    onDone = { next(LessonStep.REVIEW) },
                )

                LessonStep.LISTEN -> ListenStep(
                    lesson = lesson,
                    accent = accent,
                    canChangeAccent = accents.size > 1,
                    onChangeAccent = { accent = app.voice.nextAccent(accent) },
                    score = score,
                    onProgress = { stepProgress = it },
                    onDone = { next(LessonStep.LISTEN) },
                )

                LessonStep.SHADOW -> ShadowStep(
                    lesson = lesson,
                    accent = accent,
                    score = score,
                    onProgress = { stepProgress = it },
                    onDone = { next(LessonStep.SHADOW) },
                )

                LessonStep.RESPOND -> RespondStep(
                    card = lesson.response,
                    accent = accent,
                    score = score,
                    onDone = { next(LessonStep.RESPOND) },
                )

                LessonStep.SUMMARY -> SummaryStep(
                    lesson = lesson,
                    accent = accent,
                    score = score,
                    onExit = onExit,
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("레슨을 그만할까요?") },
            text = { Text("지금 나가면 이번 레슨의 XP는 저장되지 않아요.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false }) { Text("계속하기") }
            },
            dismissButton = {
                TextButton(onClick = onExit) { Text("나가기") }
            },
        )
    }
}

/** Common layout: scrolling content with actions pinned to the bottom, reachable by thumb. */
@Composable
fun StepLayout(
    title: String,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            content()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

fun Lesson.dictationLine() = lines[dictation.line]
