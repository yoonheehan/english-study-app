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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpokenResult(
    val text: String,
    val confidence: Float?,
    val speechSeconds: Double?,
    val hasRecording: Boolean,
)

enum class SpeechFailure { NOT_HEARD, UNAVAILABLE }

data class SpeechState(
    val listening: Boolean = false,
    val partial: String = "",
    val level: Float = 0f,
)

/**
 * Turns one spoken attempt into text. Phones differ in what they support, so it tries
 * methods in order and remembers the first that works:
 * on-device with our own recording, on-device alone, then the phone's default
 * recognizer (which may use the network if no English pack is installed).
 */
class SpeechInput(context: Context) {
    private enum class Method { DEVICE_WITH_RECORDING, DEVICE, DEFAULT }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val wavFile = File(appContext.filesDir, "recordings/last.wav")
    private val failedMethods = mutableSetOf<Method>()
    private var packRequested = false

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state

    private var recognizer: SpeechRecognizer? = null
    private var recorder: PcmRecorder? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var player: MediaPlayer? = null
    private var request: Request? = null
    private var beganAt = 0L
    private var endedAt = 0L

    private class Request(
        val wantRecording: Boolean,
        val onResult: (SpokenResult) -> Unit,
        val onFailure: (SpeechFailure) -> Unit,
    )

    private val watchdog = Runnable { finishWith { it.onFailure(SpeechFailure.NOT_HEARD) } }

    fun listen(wantRecording: Boolean, onResult: (SpokenResult) -> Unit, onFailure: (SpeechFailure) -> Unit) {
        cancel()
        request = Request(wantRecording, onResult, onFailure)
        wavFile.delete()
        beginAttempt()
    }

    private fun availableMethods(wantRecording: Boolean): List<Method> = buildList {
        val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        if (onDevice && wantRecording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Method.DEVICE_WITH_RECORDING)
        }
        if (onDevice) add(Method.DEVICE)
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) add(Method.DEFAULT)
    }.filterNot { it in failedMethods }

    private fun beginAttempt() {
        val active = request ?: return
        val method = availableMethods(active.wantRecording).firstOrNull()
        if (method == null) {
            finishWith { it.onFailure(SpeechFailure.UNAVAILABLE) }
            return
        }
        beganAt = 0
        endedAt = 0

        val speech = try {
            if (method == Method.DEFAULT) {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            }
        } catch (_: RuntimeException) {
            failedMethods += method
            beginAttempt()
            return
        }
        recognizer = speech
        speech.setRecognitionListener(listenerFor(method))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (method == Method.DEVICE_WITH_RECORDING) {
            val (read, write) = ParcelFileDescriptor.createPipe()
            readEnd = read
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, PcmRecorder.CHANNEL_COUNT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, PcmRecorder.ENCODING)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, PcmRecorder.SAMPLE_RATE)
            recorder = PcmRecorder(wavFile, write) {}.also { it.start() }
        }
        _state.value = SpeechState(listening = true)
        speech.startListening(intent)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, MAX_ATTEMPT_MS)
    }

    /** The learner tapped "done": recognition finishes on what it already heard. */
    fun finishSpeaking() {
        val activeRecorder = recorder
        if (activeRecorder != null) activeRecorder.stop() else recognizer?.stopListening()
        _state.value = _state.value.copy(listening = false)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, RESULT_WAIT_MS)
    }

    fun cancel() {
        request = null
        release()
        _state.value = SpeechState()
    }

    fun playLastRecording() {
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun listenerFor(method: Method) = object : RecognitionListener {
        override fun onBeginningOfSpeech() {
            beganAt = SystemClock.elapsedRealtime()
        }

        override fun onEndOfSpeech() {
            endedAt = SystemClock.elapsedRealtime()
            _state.value = _state.value.copy(listening = false, level = 0f)
        }

        override fun onRmsChanged(rmsdB: Float) {
            _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrEmpty()) _state.value = _state.value.copy(partial = text)
        }

        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isBlank()) {
                finishWith { it.onFailure(SpeechFailure.NOT_HEARD) }
                return
            }
            val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
            val end = if (endedAt > 0) endedAt else SystemClock.elapsedRealtime()
            val seconds = if (beganAt > 0) (end - beganAt) / 1000.0 else null
            val recorded = method == Method.DEVICE_WITH_RECORDING
            finishWith(waitForRecorder = recorded) {
                it.onResult(SpokenResult(text, confidence, seconds, recorded && wavFile.length() > WAV_HEADER_BYTES))
            }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    finishWith { it.onFailure(SpeechFailure.NOT_HEARD) }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    finishWith { it.onFailure(SpeechFailure.UNAVAILABLE) }

                else -> {
                    // This method does not work on this phone: fall through to the next.
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) requestEnglishPack()
                    failedMethods += method
                    release()
                    beginAttempt()
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** Asks once for the offline pack, so a later session can run without the network. */
    private fun requestEnglishPack() {
        if (packRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        packRequested = true
        runCatching {
            val downloader = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            downloader.triggerModelDownload(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE),
            )
            main.postDelayed({ downloader.destroy() }, PACK_REQUEST_LIFETIME_MS)
        }
    }

    private fun finishWith(waitForRecorder: Boolean = false, deliver: (Request) -> Unit) {
        val active = request
        request = null
        val finishedRecorder = recorder
        release()
        _state.value = SpeechState()
        if (active == null) return
        // The WAV header is written last, so playback must wait for the recorder to close.
        if (waitForRecorder && finishedRecorder != null) {
            main.postDelayed({ deliver(active) }, RECORDER_CLOSE_MS)
        } else {
            deliver(active)
        }
    }

    private fun release() {
        main.removeCallbacks(watchdog)
        recorder?.stop()
        recorder = null
        recognizer?.destroy()
        recognizer = null
        // Closing our copy lets a recorder blocked on a full pipe fail and exit.
        runCatching { readEnd?.close() }
        readEnd = null
    }

    fun close() {
        cancel()
        player?.release()
        player = null
    }

    private companion object {
        const val LANGUAGE = "en-US"
        const val MAX_ATTEMPT_MS = 25_000L
        const val RESULT_WAIT_MS = 10_000L
        const val RECORDER_CLOSE_MS = 250L
        const val PACK_REQUEST_LIFETIME_MS = 60_000L
        const val WAV_HEADER_BYTES = 44L
    }
}
