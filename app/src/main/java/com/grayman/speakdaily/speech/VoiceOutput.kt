package com.grayman.speakdaily.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

enum class Accent(val tag: String, val labelKo: String) {
    US("en-US", "미국"),
    UK("en-GB", "영국"),
    AU("en-AU", "호주"),
    IN("en-IN", "인도"),
    NG("en-NG", "나이지리아"),
}

/** Plays English in a chosen accent. Sentences are suspend calls, so a dialogue is a plain loop. */
class VoiceOutput(context: Context) {
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val nextId = AtomicLong()
    private var voices: Map<Accent, List<Voice>> = emptyMap()

    private val _accents = MutableStateFlow<List<Accent>>(emptyList())

    /** Accents with at least one installed voice. */
    val accents: StateFlow<List<Accent>> = _accents

    private val _notInstalled = MutableStateFlow<List<Accent>>(emptyList())
    val notInstalled: StateFlow<List<Accent>> = _notInstalled

    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady()
    }

    private fun onEngineReady() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId)

            @Deprecated("Deprecated in the platform API")
            override fun onError(utteranceId: String) = finish(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
        })
        refreshVoices()
    }

    /** Call after the user returns from the system voice-install screen. */
    fun refreshVoices() {
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val isInstalled = { voice: Voice -> TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features }
        val byAccent = Accent.entries.associateWith { accent ->
            all.filter { it.locale.toLanguageTag() == accent.tag }
        }
        voices = byAccent.mapValues { (_, list) ->
            // Offline first, then quality, so practice keeps working without a network.
            list.filter(isInstalled).sortedWith(
                compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }),
            )
        }
        _accents.value = voices.filterValues { it.isNotEmpty() }.keys.toList()
        _notInstalled.value = byAccent.filter { (accent, list) ->
            list.isNotEmpty() && voices[accent].isNullOrEmpty()
        }.keys.toList()
    }

    /** Rotates accents daily so listening never settles on one. */
    fun accentForDay(epochDay: Long): Accent {
        val available = _accents.value
        if (available.isEmpty()) return Accent.US
        return available[(epochDay % available.size).toInt()]
    }

    fun nextAccent(current: Accent): Accent {
        val available = _accents.value
        if (available.isEmpty()) return current
        return available[(available.indexOf(current) + 1) % available.size]
    }

    /** Speaks one sentence and returns when it ends, fails, or is stopped. */
    suspend fun speak(text: String, accent: Accent, speaker: Int = 0, rate: Float = 1f) {
        // The engine starts asynchronously; a lesson can open before it is ready.
        withTimeoutOrNull(ENGINE_WAIT_MS) { _accents.first { it.isNotEmpty() } }
        val list = voices[accent].orEmpty().ifEmpty { voices[Accent.US].orEmpty() }
        if (list.isNotEmpty()) tts.voice = list[speaker % list.size]
        tts.setSpeechRate(rate)
        withTimeoutOrNull(timeoutFor(text, rate)) {
            suspendCancellableCoroutine { continuation ->
                val id = "u${nextId.incrementAndGet()}"
                pending[id] = continuation
                continuation.invokeOnCancellation {
                    pending.remove(id)
                    tts.stop()
                }
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
            }
        }
    }

    fun stop() {
        tts.stop()
    }

    private fun finish(id: String) {
        pending.remove(id)?.resume(Unit)
    }

    private fun timeoutFor(text: String, rate: Float): Long =
        ((text.length * MS_PER_CHAR / rate).toLong()).coerceAtLeast(MIN_TIMEOUT_MS)

    private companion object {
        const val ENGINE_WAIT_MS = 8_000L
        const val MS_PER_CHAR = 200
        const val MIN_TIMEOUT_MS = 6_000L
    }
}
