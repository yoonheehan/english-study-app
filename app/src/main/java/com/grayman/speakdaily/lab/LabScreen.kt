package com.grayman.speakdaily.lab

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grayman.speakdaily.ai.GemmaProbe
import com.grayman.speakdaily.speech.SttMode
import com.grayman.speakdaily.speech.SttProbe
import com.grayman.speakdaily.speech.TtsProbe

private const val TEST_SENTENCE = "I'd like a large coffee and a blueberry muffin, please."
private const val TTS_SAMPLE =
    "Excuse me, could you tell me which platform the next train to the city centre leaves from?"
private const val MODEL_GB = 2.41

@Composable
fun LabScreen(viewModel: LabViewModel = viewModel()) {
    // The model download takes minutes; the screen must not sleep during it.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Phase 0 검증 랩", style = MaterialTheme.typography.headlineSmall)
        Text(
            "위에서 아래로 차례대로 눌러 주세요. 결과는 맨 아래에 쌓이고 복사할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TtsSection(viewModel.tts)
        SttSection(viewModel.stt)
        GemmaSection(viewModel.gemma, viewModel.stt)
        ReportSection(viewModel.report)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TtsSection(tts: TtsProbe) {
    val groups by tts.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Section("1. 억양 음성 (기기 TTS)") {
        Text(
            "미설치 음성이 있으면 음성 설치 화면에서 받은 뒤 '다시 확인'을 눌러 주세요.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    }
                },
            ) { Text("음성 설치") }
            OutlinedButton(onClick = tts::rescan) { Text("다시 확인") }
        }
        if (groups.isEmpty()) Text("음성을 찾는 중이거나 사용할 수 있는 음성이 없습니다.")
        groups.forEach { group ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (group.isEnglish) group.tag else "${group.tag} (영어 읽기 실험)")
                    Text(
                        "오프라인 ${group.offlineCount} · 온라인 ${group.onlineCount} · 미설치 ${group.notInstalledCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { tts.playNext(group.tag, TTS_SAMPLE) }) {
                    Text("듣기")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SttSection(stt: SttProbe) {
    val state by stt.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicPermission = it
    }

    Section("2. 음성 인식과 녹음") {
        Text("아래 문장을 읽어 주세요:", style = MaterialTheme.typography.bodySmall)
        Text("\"$TEST_SENTENCE\"", style = MaterialTheme.typography.titleSmall)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = stt::checkAvailability) { Text("지원 확인") }
            OutlinedButton(onClick = stt::downloadEnglishPack) { Text("영어팩 받기") }
        }

        if (!hasMicPermission) {
            Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("마이크 권한 허용")
            }
        } else if (state.listening) {
            Text("듣는 중: ${state.mode?.label}")
            Text(state.partial.ifEmpty { "..." })
            Button(onClick = stt::stop) { Text("말하기 끝") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SttMode.entries.forEach { mode ->
                    Button(onClick = { stt.start(mode, TEST_SENTENCE) }) { Text(mode.label) }
                }
                if (state.hasRecording) {
                    OutlinedButton(onClick = stt::playRecording) { Text("내 녹음 듣기") }
                }
            }
        }
        if (state.finalText.isNotEmpty()) Text("인식 결과: ${state.finalText}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GemmaSection(gemma: GemmaProbe, stt: SttProbe) {
    val state by gemma.state.collectAsStateWithLifecycle()
    Section("3. Gemma 4 E2B") {
        if (!state.modelComplete) {
            val progress = state.modelBytes / 2_588_147_712f
            Text("모델 2.41GB, Wi-Fi 권장. 받는 동안 앱을 열어 두세요.")
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
            Text("%.2f / %.2f GB".format(state.modelBytes / 1_073_741_824.0, MODEL_GB))
            if (state.downloading) {
                OutlinedButton(onClick = gemma::pauseDownload) { Text("일시정지") }
            } else {
                Button(onClick = gemma::download) {
                    Text(if (state.modelBytes > 0) "이어받기" else "모델 받기")
                }
            }
        } else {
            Text("로드됨: ${state.loadedBackend ?: "없음"}${if (state.busy) " · 작업 중..." else ""}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gemma.load(useGpu = true) }, enabled = !state.busy) { Text("GPU 로드") }
                OutlinedButton(onClick = { gemma.load(useGpu = false) }, enabled = !state.busy) { Text("CPU 로드") }
                val ready = state.loadedBackend != null && !state.busy
                Button(onClick = gemma::runScoringTest, enabled = ready) { Text("채점 테스트") }
                Button(onClick = { gemma.runAudioTest(stt.recordingPath) }, enabled = ready) { Text("음성 입력 테스트") }
            }
            if (state.reply.isNotEmpty()) {
                Text(state.reply, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReportSection(report: LabReport) {
    val lines by report.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Section("결과") {
        Button(
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Phase 0 results", report.text()))
            },
        ) { Text("결과 전체 복사") }
        SelectionContainer {
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}
