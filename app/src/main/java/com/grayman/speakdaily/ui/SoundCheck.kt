package com.grayman.speakdaily.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grayman.speakdaily.app
import com.grayman.speakdaily.speech.Accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Speech and the voice engine play on the media stream, so that is the volume that matters. */
private fun Context.mediaVolume(): Pair<Int, Int> {
    val audio = getSystemService(AudioManager::class.java)
    return audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}

/** Raises media volume to half on the learner's tap, and shows the system slider so they see it. */
private fun Context.raiseMediaVolume() {
    val audio = getSystemService(AudioManager::class.java)
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    audio.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        maxOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max / 2),
        AudioManager.FLAG_SHOW_UI,
    )
}

/**
 * Warns when nothing can be heard: media volume at zero, or a voice engine problem.
 * Shows nothing while sound works, so it can sit on every listening screen.
 */
@Composable
fun SoundWarning(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val problem by context.app.voice.problem.collectAsState()
    var muted by remember { mutableStateOf(context.mediaVolume().first == 0) }
    // The hardware volume keys change this outside the app; a light poll keeps the banner honest.
    LaunchedEffect(Unit) {
        while (true) {
            muted = context.mediaVolume().first == 0
            delay(VOLUME_POLL_MS)
        }
    }
    val message = when {
        muted -> "미디어 볼륨이 꺼져 있어서 소리가 안 들려요."
        problem != null -> problem
        else -> null
    } ?: return

    Row(
        modifier
            .fillMaxWidth()
            .background(Game.BadSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, tint = Game.OnBadSoft, modifier = Modifier.size(24.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Game.OnBadSoft, modifier = Modifier.weight(1f))
        if (muted) {
            SecondaryButton("볼륨 올리기", onClick = {
                context.raiseMediaVolume()
                muted = context.mediaVolume().first == 0
            })
        }
    }
}

/** A one-tap check: plays a sentence in every installed accent and says what happened. */
@Composable
fun SoundTestCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val voice = context.app.voice
    val scope = rememberCoroutineScope()
    val accents by voice.accents.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("소리 테스트", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "설치된 억양마다 짧은 문장을 차례로 들려줘요. 안 들리면 아래 상태를 확인하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoundWarning()
        SecondaryButton(
            if (running) "재생 중..." else "소리 테스트 시작",
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (running) return@SecondaryButton
                running = true
                scope.launch {
                    val (volume, max) = context.mediaVolume()
                    val list = accents.ifEmpty { listOf(Accent.US) }
                    for (accent in list) {
                        status = "${accent.labelKo} 억양 재생 중 · 미디어 볼륨 $volume/$max"
                        voice.speak("Hello! This is the ${accent.name} voice. Can you hear me?", accent)
                    }
                    status = voice.problem.value?.let { "재생 실패: $it" }
                        ?: "재생 끝 (${list.joinToString(", ") { it.labelKo }}) · 미디어 볼륨 $volume/$max. 소리가 안 들렸다면 기기 볼륨과 연결된 이어폰을 확인하세요."
                    running = false
                }
            },
        )
        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private const val VOLUME_POLL_MS = 1_000L
