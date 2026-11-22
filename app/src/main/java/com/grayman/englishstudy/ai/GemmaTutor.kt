package com.grayman.englishstudy.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TutorState(
    val downloaded: Boolean = false,
    val downloadedBytes: Long = 0,
    val downloading: Boolean = false,
    val loading: Boolean = false,
    /** "GPU" or "CPU" once the model is loaded. */
    val backend: String? = null,
    val error: String? = null,
) {
    val progress: Float get() = downloadedBytes.toFloat() / GemmaTutor.MODEL_BYTES
}

/**
 * The app's one Gemma 4 E2B engine, shared by role-play and the answer explanations.
 * The 2.6 GB model is downloaded once into the app's files (the same file the test lab uses) and
 * loaded on first use, on the GPU if it works and otherwise on the CPU. One single-thread
 * executor owns the engine and every conversation, as LiteRT-LM requires.
 */
class GemmaTutor(context: Context) {
    private val appContext = context.applicationContext
    private val modelFile = File(appContext.filesDir, MODEL_FILE_NAME)
    private val partFile = File(appContext.filesDir, "$MODEL_FILE_NAME.part")

    private val executor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = executor.asCoroutineDispatcher()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private var downloadJob: Job? = null
    private var engine: Engine? = null

    private val _state = MutableStateFlow(TutorState())
    val state: StateFlow<TutorState> = _state

    init {
        refreshFile()
    }

    private fun refreshFile() {
        val complete = modelFile.length() == MODEL_BYTES
        _state.update { it.copy(downloaded = complete, downloadedBytes = if (complete) MODEL_BYTES else partFile.length()) }
    }

    /** Downloads the model, resuming a partial file. Wi-Fi is the learner's call; the screen says so. */
    fun download() {
        if (_state.value.downloading || _state.value.downloaded) return
        downloadJob = ioScope.launch {
            _state.update { it.copy(downloading = true, error = null) }
            try {
                val resumeFrom = partFile.length()
                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) error("HTTP $status")
                // HTTP 200 means the server ignored Range, so the file starts over.
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
                            _state.update { it.copy(downloadedBytes = written) }
                        }
                    }
                }
                check(written == MODEL_BYTES) { "크기가 맞지 않아요 ($written / $MODEL_BYTES)" }
                check(partFile.renameTo(modelFile)) { "파일을 저장하지 못했어요" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                _state.update { it.copy(error = "모델을 받지 못했어요: ${e.message}. 다시 누르면 이어받아요.") }
            } finally {
                _state.update { it.copy(downloading = false) }
                refreshFile()
            }
        }
    }

    fun pauseDownload() {
        downloadJob?.cancel()
    }

    /** Frees the 2.6 GB. The model can be downloaded again later. */
    suspend fun deleteModel() {
        pauseDownload()
        withContext(engineDispatcher) {
            engine?.close()
            engine = null
        }
        modelFile.delete()
        partFile.delete()
        _state.update { TutorState() }
    }

    /** Loads the model if needed. Returns false when it is not downloaded or cannot load. */
    suspend fun ensureLoaded(): Boolean = loadMutex.withLock {
        if (engine != null) return@withLock true
        if (!_state.value.downloaded) return@withLock false
        _state.update { it.copy(loading = true, error = null) }
        val loaded = withContext(engineDispatcher) {
            listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU()).firstNotNullOfOrNull { (name, backend) ->
                try {
                    val config = EngineConfig(modelPath = modelFile.absolutePath, backend = backend, cacheDir = appContext.cacheDir.path)
                    // Stored only after initialize returns: closing an engine whose initialize threw is not allowed.
                    val started = SystemClock.elapsedRealtime()
                    engine = Engine(config).also { it.initialize() }
                    Log.i(TAG, "$name loaded in ${SystemClock.elapsedRealtime() - started}ms")
                    name
                } catch (e: Throwable) {
                    Log.w(TAG, "$name load failed", e)
                    null
                }
            }
        }
        _state.update { it.copy(loading = false, backend = loaded, error = if (loaded == null) "AI 모델을 불러오지 못했어요." else null) }
        loaded != null
    }

    data class Speed(val backend: String, val loadMs: Long, val firstTokenMs: Long, val tokensPerSecond: Double, val reply: String)

    /**
     * Measures this device: load time (0 when already loaded), time to the first words, and
     * roughly how many tokens per second it writes (English is about 4 characters a token).
     */
    suspend fun measureSpeed(): Speed {
        val wasLoaded = engine != null
        val t0 = SystemClock.elapsedRealtime()
        if (!ensureLoaded()) error(_state.value.error ?: "AI 모델이 아직 없어요.")
        val loadMs = if (wasLoaded) 0L else SystemClock.elapsedRealtime() - t0
        val start = SystemClock.elapsedRealtime()
        var first = -1L
        var reply = ""
        ask(SPEED_PROMPT).collect {
            if (first < 0) first = SystemClock.elapsedRealtime() - start
            reply = it
        }
        val writingMs = (SystemClock.elapsedRealtime() - start - first).coerceAtLeast(1)
        val speed = Speed(_state.value.backend ?: "?", loadMs, first, reply.length / 4.0 / (writingMs / 1000.0), reply)
        Log.i(TAG, "speed: backend=${speed.backend} load=${loadMs}ms first=${first}ms tps=${"%.1f".format(speed.tokensPerSecond)} chars=${reply.length}")
        return speed
    }

    /** A conversation that keeps its history, for role-play. */
    suspend fun chat(systemInstruction: String): TutorChat? {
        if (!ensureLoaded()) return null
        val conversation = withContext(engineDispatcher) {
            checkNotNull(engine).createConversation(
                ConversationConfig(systemInstruction = Contents.of(systemInstruction), samplerConfig = CHAT_SAMPLER),
            )
        }
        return TutorChat(conversation)
    }

    /** One question, one streamed answer, nothing remembered. For explanations and feedback. */
    fun ask(prompt: String): Flow<String> = flow {
        if (!ensureLoaded()) error(_state.value.error ?: "AI 모델이 아직 없어요.")
        val conversation = withContext(engineDispatcher) {
            checkNotNull(engine).createConversation(ConversationConfig(samplerConfig = EXPLAIN_SAMPLER))
        }
        try {
            val text = StringBuilder()
            conversation.sendMessageAsync(prompt).flowOn(engineDispatcher).collect { chunk ->
                text.append(chunk.toString())
                emit(text.toString())
            }
        } finally {
            withContext(engineDispatcher) { conversation.close() }
        }
    }

    inner class TutorChat internal constructor(private val conversation: Conversation) {
        private var closed = false

        /** The reply so far, growing as it streams. */
        fun send(text: String): Flow<String> = flow {
            val reply = StringBuilder()
            conversation.sendMessageAsync(text).flowOn(engineDispatcher).collect { chunk ->
                reply.append(chunk.toString())
                emit(reply.toString())
            }
        }

        suspend fun close() = withContext(engineDispatcher) {
            if (closed) return@withContext
            closed = true
            conversation.close()
        }
    }

    companion object {
        private const val TAG = "GemmaTutor"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"
        const val MODEL_BYTES = 2_588_147_712L
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 256 * 1024
        private const val SPEED_PROMPT = "Describe a relaxing weekend in about eight sentences."
        private val CHAT_SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
        private val EXPLAIN_SAMPLER = SamplerConfig(topK = 10, topP = 0.9, temperature = 0.3)
    }
}
