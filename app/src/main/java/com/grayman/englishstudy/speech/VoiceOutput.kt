package com.grayman.englishstudy.speech

import android.content.Context
import android.content.Intent
import android.util.Log
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
    private val appContext = context.applicationContext

    /**
     * Google's engine ships the most English accents (US, UK, Australia, India, Nigeria).
     * Phones such as Galaxy devices default to their own engine with fewer, so prefer
     * Google's whenever it is installed. This changes only this app, not system settings.
     */
    val enginePackage: String? = run {
        val installed = appContext.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }
        if (GOOGLE_TTS in installed) GOOGLE_TTS else null
    }

    /** Opens the voice download screen of the engine this app actually uses. */
    fun installVoicesIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
        enginePackage?.let { setPackage(it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val nextId = AtomicLong()
    private var voices: Map<Accent, List<Voice>> = emptyMap()

    private val _accents = MutableStateFlow<List<Accent>>(emptyList())

    /** Accents with at least one installed voice. */
    val accents: StateFlow<List<Accent>> = _accents

    private val _notInstalled = MutableStateFlow<List<Accent>>(emptyList())
    val notInstalled: StateFlow<List<Accent>> = _notInstalled

    private val _problem = MutableStateFlow<String?>(null)

    /** Why speech last failed, in words the learner can act on; null while speech works. */
    val problem: StateFlow<String?> = _problem

    private val tts = TextToSpeech(appContext, { status -> onInit(status) }, enginePackage)

    private fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            onEngineReady()
        } else {
            _problem.value = "음성 엔진을 시작하지 못했어요. 설정 > 텍스트 음성 변환에서 기본 엔진을 확인해 주세요."
        }
    }

    private fun onEngineReady() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                _problem.value = null
                finish(utteranceId)
            }

            @Deprecated("Deprecated in the platform API")
            override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

            override fun onError(utteranceId: String, errorCode: Int) {
                _problem.value = when (errorCode) {
                    TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                        "이 억양 음성은 인터넷이 필요해요. 연결을 확인하거나 음성을 내려받아 주세요."
                    TextToSpeech.ERROR_NOT_INSTALLED_YET -> "음성 데이터를 아직 내려받는 중이에요. 잠시 뒤 다시 해 보세요."
                    TextToSpeech.ERROR_OUTPUT -> "소리를 내보내지 못했어요. 블루투스나 출력 기기를 확인해 주세요."
                    else -> "음성을 재생하지 못했어요 (오류 $errorCode)."
                }
                finish(utteranceId)
            }
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
        Log.i(
            TAG,
            "engine=${tts.defaultEngine} " + byAccent.entries.joinToString { (accent, list) ->
                "${accent.tag}: ${list.count(isInstalled)}/${list.size}"
            },
        )
        if (_accents.value.isEmpty()) {
            _problem.value = "영어 음성이 설치되어 있지 않아요. '억양 음성 받기'에서 영어(미국)를 받아 주세요."
        }
        _notInstalled.value = byAccent.filter { (accent, list) ->
            list.isNotEmpty() && voices[accent].isNullOrEmpty()
        }.keys.toList()
    }

    private val prefs = appContext.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val _preferred = MutableStateFlow(prefs.getString(KEY_PREFERRED, null)?.let { n -> Accent.entries.firstOrNull { it.name == n } })

    /** The accent the learner picked, or null for "mixed" (rotate by day and by question). */
    val preferred: StateFlow<Accent?> = _preferred

    fun setPreferred(accent: Accent?) {
        _preferred.value = accent
        prefs.edit().putString(KEY_PREFERRED, accent?.name).apply()
    }

    /** The picked accent when it is installed, otherwise the first installed one. */
    fun defaultAccent(): Accent {
        val available = _accents.value
        return _preferred.value?.takeIf { it in available } ?: available.firstOrNull() ?: Accent.US
    }

    /** Accents a listening test may use: just the picked one, or every installed accent when mixed. */
    fun accentsForTest(): List<Accent> {
        val available = _accents.value.ifEmpty { listOf(Accent.US) }
        return _preferred.value?.takeIf { it in available }?.let { listOf(it) } ?: available
    }

    /** The picked accent, or one that rotates daily so listening never settles on one. */
    fun accentForDay(epochDay: Long): Accent {
        _preferred.value?.takeIf { it in _accents.value }?.let { return it }
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
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                    _problem.value = "음성 엔진이 재생 요청을 거절했어요. 앱을 다시 열어 보세요."
                    finish(id)
                }
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
        const val KEY_PREFERRED = "preferred_accent"
        const val ENGINE_WAIT_MS = 8_000L
        const val GOOGLE_TTS = "com.google.android.tts"
        const val TAG = "VOICE"
        const val MS_PER_CHAR = 200
        const val MIN_TIMEOUT_MS = 6_000L
    }
}
