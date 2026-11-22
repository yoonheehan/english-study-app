package com.grayman.englishstudy.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.grayman.englishstudy.app
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Download state of the on-device AI, with download, pause and delete. Shown in the speaking tab. */
@Composable
fun AiModelCard(modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val state by app.tutor.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(if (state.downloaded) Game.GoodSoft else Game.LagoonSoft, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (state.downloaded) "AI 준비 완료" + (state.backend?.let { " · $it" } ?: "") else "AI 모델 받기 (Gemma 4 E2B, 2.6GB)",
            style = MaterialTheme.typography.titleMedium,
            color = if (state.downloaded) Game.OnGoodSoft else Game.LagoonEdge,
        )
        Text(
            when {
                state.downloaded -> "역할극과 AI 해설이 이 태블릿 안에서 돌아가요. 인터넷이 없어도 돼요."
                else -> "역할극 상대와 틀린 문제 해설을 맡을 AI예요. 한 번만 받으면 인터넷 없이 써요. 와이파이에서 받아 주세요."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.downloaded) {
            if (state.downloadedBytes > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameProgressBar(state.progress, Modifier.weight(1f), height = 10.dp)
                    Text(
                        "%.1f / 2.6GB".format(state.downloadedBytes / 1_000_000_000.0),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.downloading) {
                    SecondaryButton("일시정지", onClick = app.tutor::pauseDownload, modifier = Modifier.width(160.dp))
                } else {
                    ChunkyButton(
                        if (state.downloadedBytes > 0) "이어받기" else "받기",
                        onClick = app.tutor::download,
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.width(200.dp),
                    )
                }
            }
        } else {
            var speed by remember { mutableStateOf<String?>(null) }
            var measuring by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(if (measuring) "측정 중…" else "이 기기 AI 속도 측정", onClick = {
                    if (measuring) return@SecondaryButton
                    measuring = true
                    speed = "AI를 불러오고 짧은 글을 쓰게 해 볼게요. 처음엔 1분 가까이 걸릴 수 있어요…"
                    scope.launch {
                        speed = try {
                            val r = app.tutor.measureSpeed()
                            val verdict = when {
                                r.tokensPerSecond >= 15 -> "빠름: 대화가 거의 바로바로 나와요."
                                r.tokensPerSecond >= 7 -> "보통: 한 문장에 1~2초쯤 걸려요."
                                else -> "느림: 답이 나오기까지 기다려야 해요."
                            }
                            "${r.backend} 사용 · " +
                                (if (r.loadMs > 0) "불러오기 %.1f초 · ".format(r.loadMs / 1000.0) else "") +
                                "첫 응답 %.1f초 · 초당 약 %.1f토큰\n".format(r.firstTokenMs / 1000.0, r.tokensPerSecond) + verdict
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "측정하지 못했어요: ${e.message}"
                        } finally {
                            measuring = false
                        }
                    }
                })
                TextButton(onClick = { confirmDelete = true }) { Text("AI 모델 지우기 (2.6GB 확보)") }
            }
            speed?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Game.Bad) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("AI 모델을 지울까요?") },
            text = { Text("역할극과 AI 해설을 쓰려면 2.6GB를 다시 받아야 해요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.tutor.deleteModel() }
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/**
 * "AI 해설" button that streams a Korean explanation from [prompt]. When the model is not
 * downloaded it says where to get it instead.
 */
@Composable
fun AiExplainBox(prompt: () -> String, modifier: Modifier = Modifier, label: String = "AI 해설") {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val state by app.tutor.state.collectAsState()
    var text by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (text == null && !running) {
            SecondaryButton(
                label,
                icon = Icons.Rounded.AutoAwesome,
                onClick = {
                    if (!state.downloaded) {
                        text = "AI 모델이 아직 없어요. 말하기 탭에서 먼저 받아 주세요."
                        return@SecondaryButton
                    }
                    running = true
                    text = if (state.backend == null) "AI를 불러오는 중이에요. 처음 한 번은 조금 걸려요…" else "생각하는 중…"
                    scope.launch {
                        try {
                            app.tutor.ask(prompt()).collect { text = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            text = "해설을 만들지 못했어요: ${e.message}"
                        } finally {
                            running = false
                        }
                    }
                },
            )
        }
        text?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, Game.Lagoon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }
    }
}
