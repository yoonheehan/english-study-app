package com.grayman.speakdaily.speech

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.grayman.speakdaily.domain.TextMatcher
import com.grayman.speakdaily.lab.LabReport
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SttMode(val label: String) {
    PLAIN("일반 인식"),
    WITH_RECORDING("녹음과 인식 동시"),
}

data class SttState(
    val listening: Boolean = false,
    val mode: SttMode? = null,
    val partial: String = "",
    val finalText: String = "",
    val hasRecording: Boolean = false,
)

/**
 * Tests on-device recognition. All SpeechRecognizer calls stay on the main thread,
 * which is what the platform API requires.
 */
class SttProbe(context: Context, private val report: LabReport) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val wavFile = File(appContext.filesDir, "lab/recording.wav")

    private val _state = MutableStateFlow(SttState(hasRecording = wavFile.length() > WAV_HEADER))
    val state: StateFlow<SttState> = _state

    private var recognizer: SpeechRecognizer? = null
    private var recorder: PcmRecorder? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var player: MediaPlayer? = null
    private var stoppedAt = 0L
    private val watchdog = Runnable {
        report.add("STT", "결과 없이 ${WATCHDOG_MS / 1000}초 경과, 중단 (${_state.value.mode?.label})")
        finish()
    }

    val recordingPath: String get() = wavFile.absolutePath

    fun checkAvailability() {
        val available = SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        report.add("STT", "온디바이스 인식 사용 가능=$available, API=${Build.VERSION.SDK_INT}")
        if (!available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        checker.checkRecognitionSupport(
            recognizerIntent(),
            appContext.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val english = { list: List<String> -> list.filter { it.startsWith("en") } }
                    report.add("STT", "설치된 언어: ${english(support.installedOnDeviceLanguages)}")
                    report.add("STT", "지원 언어: ${english(support.supportedOnDeviceLanguages)}")
                    report.add("STT", "다운로드 대기: ${english(support.pendingOnDeviceLanguages)}")
                    checker.destroy()
                }

                override fun onError(error: Int) {
                    report.add("STT", "지원 언어 확인 실패: ${errorName(error)}")
                    checker.destroy()
                }
            },
        )
    }

    fun downloadEnglishPack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            report.add("STT", "언어팩 다운로드 API는 Android 13 이상에서만 지원")
            return
        }
        val downloader = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        report.add("STT", "en-US 언어팩 다운로드 요청")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ reports the outcome; older versions give no feedback at all.
            downloader.triggerModelDownload(
                recognizerIntent(),
                appContext.mainExecutor,
                object : ModelDownloadListener {
                    private var lastLogged = -PROGRESS_LOG_STEP

                    override fun onProgress(completedPercent: Int) {
                        if (completedPercent >= lastLogged + PROGRESS_LOG_STEP) {
                            lastLogged = completedPercent
                            report.add("STT", "언어팩 다운로드 $completedPercent%")
                        }
                    }

                    override fun onSuccess() {
                        report.add("STT", "언어팩 다운로드 완료")
                        downloader.destroy()
                    }

                    override fun onScheduled() {
                        report.add("STT", "언어팩 다운로드 예약됨 (Wi-Fi나 충전 중일 때 진행될 수 있음)")
                        downloader.destroy()
                    }

                    override fun onError(error: Int) {
                        report.add("STT", "언어팩 다운로드 실패: ${errorName(error)}")
                        downloader.destroy()
                    }
                },
            )
        } else {
            downloader.triggerModelDownload(recognizerIntent())
            main.postDelayed({ downloader.destroy() }, DOWNLOADER_LIFETIME_MS)
        }
    }

    fun start(mode: SttMode, expected: String) {
        finish()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            report.add("STT", "온디바이스 인식을 쓸 수 없는 기기")
            return
        }
        if (mode == SttMode.WITH_RECORDING && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            report.add("STT", "녹음 동시 인식은 Android 13 이상 필요, 현재 API=${Build.VERSION.SDK_INT}")
            return
        }

        val speech = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } catch (e: RuntimeException) {
            report.add("STT", "인식기 생성 실패: ${e.message}")
            return
        }
        recognizer = speech
        speech.setRecognitionListener(listener(mode, expected))

        val intent = recognizerIntent()
        if (mode == SttMode.WITH_RECORDING) {
            val (read, write) = ParcelFileDescriptor.createPipe()
            readEnd = read
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, PcmRecorder.CHANNEL_COUNT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, PcmRecorder.ENCODING)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, PcmRecorder.SAMPLE_RATE)
            recorder = PcmRecorder(wavFile, write) { bytes ->
                main.post {
                    report.add("STT", "녹음 파일 ${bytes / 1024}KB, ${bytes / BYTES_PER_SECOND}초")
                    _state.value = _state.value.copy(hasRecording = bytes > 0)
                }
            }.also { it.start() }
        }

        _state.value = _state.value.copy(listening = true, mode = mode, partial = "", finalText = "")
        speech.startListening(intent)
        report.add("STT", "시작: ${mode.label}")
    }

    /** Ends the utterance: recognition then finishes on what was already heard. */
    fun stop() {
        stoppedAt = SystemClock.elapsedRealtime()
        val activeRecorder = recorder
        if (activeRecorder != null) {
            // Closing the pipe is the end-of-audio signal for a recognizer fed by our recorder.
            activeRecorder.stop()
        } else {
            recognizer?.stopListening()
        }
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    fun playRecording() {
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                start()
            }
        }.onFailure { report.add("STT", "녹음 재생 실패: ${it.message}") }.getOrNull()
    }

    private fun listener(mode: SttMode, expected: String) = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            _state.value = _state.value.copy(partial = text)
        }

        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
            val match = TextMatcher.compare(expected, text)
            val latency = if (stoppedAt > 0) SystemClock.elapsedRealtime() - stoppedAt else -1
            report.add(
                "STT",
                "${mode.label} 결과: \"$text\" / 일치 ${match.score}% / 신뢰도=$confidence / " +
                    "빠진 단어=${match.missing} / 다른 단어=${match.extra} / 지연=${latency}ms",
            )
            _state.value = _state.value.copy(finalText = text)
            finish()
        }

        override fun onError(error: Int) {
            report.add("STT", "${mode.label} 오류: ${errorName(error)}")
            finish()
        }

        override fun onEndOfSpeech() {
            if (stoppedAt == 0L) stoppedAt = SystemClock.elapsedRealtime()
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finish() {
        main.removeCallbacks(watchdog)
        stoppedAt = 0L
        recorder?.stop()
        recorder = null
        recognizer?.destroy()
        recognizer = null
        // Closing our copy lets a recorder blocked on a full pipe fail and exit.
        runCatching { readEnd?.close() }
        readEnd = null
        _state.value = _state.value.copy(listening = false)
    }

    fun close() {
        finish()
        player?.release()
        player = null
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE (언어팩 필요)"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
        else -> "code=$code"
    }

    private companion object {
        const val LANGUAGE = "en-US"
        const val WATCHDOG_MS = 15_000L
        const val DOWNLOADER_LIFETIME_MS = 60_000L
        const val PROGRESS_LOG_STEP = 25
        const val WAV_HEADER = 44L
        const val BYTES_PER_SECOND = 32_000L
    }
}
