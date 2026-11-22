package com.grayman.englishstudy.speech

import android.content.ComponentName
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
import android.util.Log
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
 * methods in order and skips those that failed:
 * - the Google app's recognizer (online, needs no language pack), when installed;
 * - on-device recognition, with our own recording when the answer is to be played back;
 * - the phone's default recognizer.
 * On some phones (this Galaxy Tab) the default and on-device recognizers are Google's offline
 * ones, which refuse English until a language pack is downloaded, and that download can fail.
 * The Google app's recognizer avoids that, so it comes first unless a recording is wanted.
 */
class SpeechInput(context: Context) {
    private enum class Method { GOOGLE_APP, DEVICE_WITH_RECORDING, DEVICE, DEFAULT }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val wavFile = File(appContext.filesDir, "recordings/last.wav")
    private val failedMethods = mutableSetOf<Method>()

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state

    private var recognizer: SpeechRecognizer? = null
    private var recorder: PcmRecorder? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var player: MediaPlayer? = null
    private var request: Request? = null
    private var beganAt = 0L
    private var endedAt = 0L

    // One spoken answer can span several recognizer sessions: a pause ("um...") ends a session,
    // so the text so far is kept and listening starts again until the learner taps "done".
    private val segments = mutableListOf<String>()
    private var stopRequested = false
    private var sessionStartedAt = 0L
    private var firstBeganAt = 0L
    private var emptyRestarts = 0
    private var lastConfidence: Float? = null

    private class Request(
        val wantRecording: Boolean,
        val onResult: (SpokenResult) -> Unit,
        val onFailure: (SpeechFailure) -> Unit,
    )

    /** A session that runs too long is asked to stop; its result then arrives as usual. */
    private val watchdog = Runnable {
        if (stopRequested || recognizer == null) finishWithSegments() else {
            recognizer?.stopListening()
            main.postDelayed(finalWatchdog, RESULT_WAIT_MS)
        }
    }
    private val finalWatchdog = Runnable { finishWithSegments() }


    fun listen(wantRecording: Boolean, onResult: (SpokenResult) -> Unit, onFailure: (SpeechFailure) -> Unit) {
        cancel()
        request = Request(wantRecording, onResult, onFailure)
        wavFile.delete()
        segments.clear()
        stopRequested = false
        sessionStartedAt = SystemClock.elapsedRealtime()
        firstBeganAt = 0
        emptyRestarts = 0
        lastConfidence = null
        beginAttempt()
    }

    private val googleApp: ComponentName? by lazy {
        val component = ComponentName(GOOGLE_APP_PACKAGE, GOOGLE_APP_SERVICE)
        runCatching { appContext.packageManager.getServiceInfo(component, 0) }.getOrNull()?.let { component }
    }

    private fun availableMethods(wantRecording: Boolean): List<Method> = buildList {
        val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val recordable = onDevice && wantRecording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        // Only playing the answer back needs our own recording, which only on-device recognition allows.
        if (recordable) add(Method.DEVICE_WITH_RECORDING)
        if (googleApp != null) add(Method.GOOGLE_APP)
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
            when (method) {
                Method.GOOGLE_APP -> SpeechRecognizer.createSpeechRecognizer(appContext, googleApp)
                Method.DEFAULT -> SpeechRecognizer.createSpeechRecognizer(appContext)
                else -> SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
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
            // Learners pause to think; ask for long silences before a session ends (some engines ignore this).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MIN_LENGTH_MS)
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
        _state.value = SpeechState(listening = true, partial = segments.joinToString(" "))
        speech.startListening(intent)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, MAX_ATTEMPT_MS)
    }

    /** The learner tapped "done": recognition finishes on what it already heard. */
    fun finishSpeaking() {
        stopRequested = true
        if (recognizer == null) {
            finishWithSegments()
            return
        }
        val activeRecorder = recorder
        if (activeRecorder != null) activeRecorder.stop() else recognizer?.stopListening()
        _state.value = _state.value.copy(listening = false)
        main.removeCallbacks(watchdog)
        main.postDelayed(finalWatchdog, RESULT_WAIT_MS)
    }

    private fun keepListening(): Boolean =
        !stopRequested && SystemClock.elapsedRealtime() - sessionStartedAt < MAX_SESSION_MS

    /** Starts the next recognizer session of the same answer. */
    private fun continueListening() {
        release()
        beginAttempt()
    }

    /** Delivers everything heard so far, or "not heard" when nothing was. */
    private fun finishWithSegments(method: Method? = null) {
        val text = segments.joinToString(" ").trim()
        if (text.isEmpty()) {
            finishWith { it.onFailure(SpeechFailure.NOT_HEARD) }
            return
        }
        val end = if (endedAt > 0) endedAt else SystemClock.elapsedRealtime()
        val seconds = if (firstBeganAt > 0) (end - firstBeganAt) / 1000.0 else null
        val recorded = method == Method.DEVICE_WITH_RECORDING
        finishWith(waitForRecorder = recorded) {
            it.onResult(SpokenResult(text, lastConfidence, seconds, recorded && wavFile.length() > WAV_HEADER_BYTES))
        }
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
            if (firstBeganAt == 0L) firstBeganAt = beganAt
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
            if (!text.isNullOrEmpty()) _state.value = _state.value.copy(partial = (segments + text).joinToString(" "))
        }

        override fun onResults(results: Bundle) {
            main.removeCallbacks(finalWatchdog)
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                segments += text.trim()
                emptyRestarts = 0
                lastConfidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: lastConfidence
            } else {
                emptyRestarts++
            }
            if (keepListening() && emptyRestarts <= MAX_EMPTY_RESTARTS) continueListening() else finishWithSegments(method)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // A pause, not the end: keep listening unless the learner said "done" or it has
                    // been quiet for several sessions in a row.
                    emptyRestarts++
                    if (keepListening() && emptyRestarts <= MAX_EMPTY_RESTARTS) continueListening() else finishWithSegments(method)
                }

                // The Google app may lack its own microphone permission; the other recognizers may still work.
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (method == Method.GOOGLE_APP) {
                    Log.i(TAG, "the Google app's recognizer is not allowed to listen; trying the next one")
                    failedMethods += method
                    release()
                    beginAttempt()
                } else {
                    finishWith { it.onFailure(SpeechFailure.UNAVAILABLE) }
                }

                else -> {
                    // This method does not work on this phone (no English pack, no network...):
                    // fall through to the next, without asking for a pack download.
                    Log.i(TAG, "speech method $method failed with error $error")
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
        main.removeCallbacks(finalWatchdog)
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
        const val MAX_ATTEMPT_MS = 60_000L
        const val MAX_SESSION_MS = 180_000L
        const val MAX_EMPTY_RESTARTS = 2
        const val SILENCE_MS = 4_000L
        const val MIN_LENGTH_MS = 3_000L
        const val RESULT_WAIT_MS = 10_000L
        const val RECORDER_CLOSE_MS = 250L
        const val TAG = "SpeechInput"
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        const val GOOGLE_APP_SERVICE = "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        const val WAV_HEADER_BYTES = 44L
    }
}
