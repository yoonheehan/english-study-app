package com.grayman.speakdaily.ui.lesson

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grayman.speakdaily.app
import com.grayman.speakdaily.speech.SpeechFailure
import com.grayman.speakdaily.speech.SpokenResult
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.MicButton

/** The microphone with its permission, listening, and failure states in one place. */
@Composable
fun SpeakPanel(
    wantRecording: Boolean,
    onResult: (SpokenResult) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.app
    val state by app.speech.state.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    DisposableEffect(Unit) { onDispose { app.speech.cancel() } }

    fun start() {
        app.voice.stop()
        message = null
        active = true
        app.speech.listen(
            wantRecording = wantRecording,
            onResult = {
                active = false
                onResult(it)
            },
            onFailure = { failure ->
                active = false
                when (failure) {
                    SpeechFailure.NOT_HEARD -> message = "잘 안 들렸어요. 다시 말해 볼까요?"
                    SpeechFailure.UNAVAILABLE -> {
                        unavailable = true
                        message = "이 폰에서 음성 인식을 쓸 수 없어요. 기록 탭의 검증 랩에서 영어팩을 받아 주세요."
                    }
                }
            },
        )
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            !hasPermission -> {
                Text(
                    "말하기 연습에는 마이크가 필요해요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChunkyButton(
                    "마이크 켜기",
                    onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            !unavailable -> {
                Text(
                    state.partial.ifEmpty { "" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 24.dp),
                )
                MicButton(
                    listening = active && state.listening,
                    level = state.level,
                    onClick = { if (active) app.speech.finishSpeaking() else start() },
                )
                Text(
                    when {
                        active && state.listening -> "듣고 있어요. 다 말하면 눌러 주세요"
                        active -> "알아듣는 중..."
                        else -> "눌러서 말하기"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Game.Bad, textAlign = TextAlign.Center)
        }
        Text(
            "지금은 말할 수 없어요",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(10.dp),
        )
    }
}
