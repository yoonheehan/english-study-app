package com.grayman.speakdaily.ui.listening

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.grayman.speakdaily.app
import com.grayman.speakdaily.data.Level
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.Mascot
import com.grayman.speakdaily.ui.MascotMood
import com.grayman.speakdaily.ui.SecondaryButton
import com.grayman.speakdaily.ui.SpeechBubble
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** Everything needed to start a test. `attempt` makes "try again" a new screen with a new draw. */
@Serializable
data class ListeningTestSettings(
    val levelCode: String,
    val full: Boolean,
    val practice: Boolean,
    val attempt: Int = 0,
)

@Composable
fun ListeningTestTab(profileLevel: Level, onStart: (ListeningTestSettings) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var level by rememberSaveable(profileLevel) { mutableStateOf(profileLevel) }
    var full by rememberSaveable { mutableStateOf(false) }
    var practice by rememberSaveable { mutableStateOf(false) }
    val history by remember { app.progress.recentListeningResults(HISTORY_SIZE) }.collectAsState(initial = emptyList())
    val notInstalled by app.voice.notInstalled.collectAsState()
    LifecycleResumeEffect(Unit) {
        app.voice.refreshVoices()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("듣기 테스트", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotMood.LISTENING, size = 84.dp)
            Spacer(Modifier.width(8.dp))
            SpeechBubble(
                "토익 리스닝처럼 질문-응답, 짧은 대화, 안내방송 세 파트로 나와요. 문제마다 억양도 바뀌어요!",
                Modifier.weight(1f),
            )
        }

        SectionLabel("레벨")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Level.entries.forEach { option ->
                val selected = option == level
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.RadioButton) { level = option },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.code.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "${level.labelKo} · ${level.descriptionKo}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("문제 수")
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard("10문제", "약 5분", !full, Modifier.weight(1f)) { full = false }
            ChoiceCard("20문제", "약 10분", full, Modifier.weight(1f)) { full = true }
        }

        SectionLabel("모드")
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard("실전", "한 번만 들려줘요", !practice, Modifier.weight(1f)) { practice = false }
            ChoiceCard("연습", "몇 번이고 다시 들어요", practice, Modifier.weight(1f)) { practice = true }
        }

        if (notInstalled.isNotEmpty()) {
            Text(
                "억양을 더 받으면 문제마다 다양한 억양이 나와요: " + notInstalled.joinToString(", ") { it.labelKo },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                "억양 음성 받기",
                icon = Icons.Rounded.Download,
                onClick = {
                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ChunkyButton(
            "테스트 시작",
            icon = Icons.Rounded.Headphones,
            onClick = { onStart(ListeningTestSettings(level.code, full, practice)) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (history.isNotEmpty()) {
            SectionLabel("최근 기록")
            history.forEach { result ->
                val percent = if (result.total == 0) 0 else result.correct * 100 / result.total
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        LocalDate.ofEpochDay(result.epochDay).format(DateTimeFormatter.ofPattern("M/d")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        result.levelCode.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Game.LagoonEdge,
                        modifier = Modifier.width(36.dp),
                    )
                    Text(
                        "${result.correct} / ${result.total}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$percent%", style = MaterialTheme.typography.titleMedium, color = percentColor(percent))
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Game.LagoonSoft else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (selected) Game.Lagoon else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun percentColor(percent: Int): Color = when {
    percent >= 75 -> Game.Good
    percent >= 50 -> Game.Flame
    else -> Game.Bad
}

private const val HISTORY_SIZE = 5
