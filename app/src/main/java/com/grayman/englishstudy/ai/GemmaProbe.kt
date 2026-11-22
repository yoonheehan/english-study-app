package com.grayman.englishstudy.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.grayman.englishstudy.lab.LabReport
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GemmaState(
    val modelBytes: Long = 0,
    val modelComplete: Boolean = false,
    val downloading: Boolean = false,
    val loadedBackend: String? = null,
    val busy: Boolean = false,
    val reply: String = "",
)

/**
 * Measures whether this phone can really run Gemma 4 E2B. One single-thread
 * executor owns the engine: creating, generating, and closing all run on it.
 */
class GemmaProbe(context: Context, private val report: LabReport) {
    private val appContext = context.applicationContext
    private val modelFile = File(appContext.filesDir, MODEL_FILE_NAME)
    private val partFile = File(appContext.filesDir, "$MODEL_FILE_NAME.part")
    private val prefs = appContext.getSharedPreferences("lab", Context.MODE_PRIVATE)

    private val executor = Executors.newSingleThreadExecutor()
    private val engineScope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var engine: Engine? = null

    private val _state = MutableStateFlow(GemmaState())
    val state: StateFlow<GemmaState> = _state

    init {
        refreshFileState()
        reportDevice()
        // A native out-of-memory kill skips every catch block, so the marker survives it.
        prefs.getString(KEY_LOADING_MARKER, null)?.let { backend ->
            report.add("GEMMA", "이전 실행에서 $backend 로드 중 앱이 강제 종료됨 (메모리 부족 가능성)")
            prefs.edit().remove(KEY_LOADING_MARKER).apply()
        }
    }

    private fun refreshFileState() {
        val complete = modelFile.length() == MODEL_BYTES
        _state.update {
            it.copy(modelComplete = complete, modelBytes = if (complete) MODEL_BYTES else partFile.length())
        }
    }

    private fun reportDevice() {
        val memory = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val free = StatFs(appContext.filesDir.path).availableBytes
        report.add(
            "DEVICE",
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "SoC=${Build.SOC_MODEL}, RAM=${memory.totalMem.toGb()}GB, 사용 가능=${memory.availMem.toGb()}GB, " +
                "저장공간 여유=${free.toGb()}GB",
        )
    }

    fun download() {
        if (_state.value.downloading || _state.value.modelComplete) return
        downloadJob = ioScope.launch {
            _state.update { it.copy(downloading = true) }
            val started = SystemClock.elapsedRealtime()
            try {
                val resumeFrom = partFile.length()
                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                    error("HTTP $status")
                }
                // HTTP 200 means the server ignored Range, so the file restarts from zero.
                val append = status == HttpURLConnection.HTTP_PARTIAL
                var written = if (append) resumeFrom else 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partFile, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            _state.update { it.copy(modelBytes = written) }
                        }
                    }
                }
                check(written == MODEL_BYTES) { "크기 불일치: $written / $MODEL_BYTES" }
                check(partFile.renameTo(modelFile)) { "파일 이름 변경 실패" }
                val seconds = (SystemClock.elapsedRealtime() - started) / 1000
                report.add("GEMMA", "모델 다운로드 완료, ${seconds}초")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    report.add("GEMMA", "다운로드 일시정지. 다시 누르면 이어받음")
                    throw e
                }
                report.add("GEMMA", "다운로드 실패: ${e.message}. 다시 누르면 이어받음")
            } finally {
                _state.update { it.copy(downloading = false) }
                refreshFileState()
            }
        }
    }

    fun pauseDownload() {
        downloadJob?.cancel()
    }

    fun load(useGpu: Boolean) {
        engineScope.launch {
            val name = if (useGpu) "GPU" else "CPU"
            if (_state.value.loadedBackend == name) return@launch
            if (!modelFile.exists()) {
                report.add("GEMMA", "모델 파일이 없음")
                return@launch
            }
            _state.update { it.copy(busy = true, loadedBackend = null) }
            closeEngine()
            prefs.edit().putString(KEY_LOADING_MARKER, name).commit()
            val started = SystemClock.elapsedRealtime()
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                    audioBackend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.path,
                )
                engine = Engine(config).also { it.initialize() }
                val elapsed = SystemClock.elapsedRealtime() - started
                report.add("GEMMA", "$name 로드 성공: ${elapsed}ms, ${availableMemory()}")
                _state.update { it.copy(loadedBackend = name) }
            } catch (e: Throwable) {
                report.add("GEMMA", "$name 로드 실패: ${e.message}")
            } finally {
                prefs.edit().remove(KEY_LOADING_MARKER).apply()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Scores a good and a poor answer, so a basic sanity check of the scoring comes with the timing. */
    fun runScoringTest() {
        val good = "Oh wow, it's been ages! I've been pretty busy with work, but I'm doing great. How about you?"
        val poor = "Yes I am go work. Long time."
        generate("채점-좋은 답변", Contents.of(scoringPrompt(good))) {
            generate("채점-나쁜 답변", Contents.of(scoringPrompt(poor)))
        }
    }

    fun runAudioTest(wavPath: String) {
        if (!File(wavPath).exists()) {
            report.add("GEMMA", "먼저 '녹음과 인식 동시' 테스트로 녹음을 만들어 주세요")
            return
        }
        generate(
            "음성 입력",
            Contents.of(
                Content.AudioFile(wavPath),
                Content.Text("Transcribe this English speech exactly. Then list up to two pronunciation problems you can hear."),
            ),
        )
    }

    private fun generate(label: String, input: Contents, then: (() -> Unit)? = null) {
        engineScope.launch {
            val activeEngine = engine
            if (activeEngine == null) {
                report.add("GEMMA", "먼저 모델을 로드해 주세요")
                return@launch
            }
            _state.update { it.copy(busy = true, reply = "") }
            val started = SystemClock.elapsedRealtime()
            var firstChunkMs = -1L
            val output = StringBuilder()
            try {
                activeEngine.createConversation(ConversationConfig()).use { conversation ->
                    conversation.sendMessageAsync(input).collect { chunk ->
                        if (firstChunkMs < 0) firstChunkMs = SystemClock.elapsedRealtime() - started
                        output.append(chunk.toString())
                        _state.update { it.copy(reply = output.toString()) }
                    }
                }
                val totalMs = SystemClock.elapsedRealtime() - started
                val generatingMs = (totalMs - firstChunkMs).coerceAtLeast(1)
                report.add(
                    "GEMMA",
                    "$label [${_state.value.loadedBackend}] 첫 응답 ${firstChunkMs}ms, 전체 ${totalMs}ms, " +
                        "약 ${output.length * 1000 / generatingMs}자/초, ${availableMemory()}",
                )
                report.add("GEMMA", "$label 응답: $output")
            } catch (e: Throwable) {
                report.add("GEMMA", "$label 실패: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
            then?.invoke()
        }
    }

    private fun scoringPrompt(answer: String) = """
        You are an English speaking coach for a Korean learner.
        Situation: an old friend says "Hi! Long time no see. How have you been?"
        The learner answered out loud: "$answer"
        Reply with only JSON in this shape:
        {"task":0-100,"grammar":0-100,"natural":0-100,"fix":"the most important fix, in Korean","better":"a better answer in English"}
    """.trimIndent()

    private fun availableMemory(): String {
        val memory = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        return "RAM 여유 ${memory.availMem.toGb()}GB"
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
    }

    fun close() {
        ioScope.cancel()
        engineScope.launch {
            closeEngine()
            executor.shutdown()
        }.invokeOnCompletion { engineScope.cancel() }
    }

    private fun Long.toGb(): String = "%.1f".format(this / 1_073_741_824.0)

    private companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"
        const val MODEL_BYTES = 2_588_147_712L
        const val KEY_LOADING_MARKER = "gemma_loading_backend"
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 256 * 1024
    }
}
