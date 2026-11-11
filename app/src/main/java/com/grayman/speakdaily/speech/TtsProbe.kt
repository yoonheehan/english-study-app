package com.grayman.speakdaily.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.grayman.speakdaily.lab.LabReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AccentGroup(
    val tag: String,
    val isEnglish: Boolean,
    val offlineCount: Int,
    val onlineCount: Int,
    val notInstalledCount: Int,
    val nextVoiceName: String,
)

/** Lists which accents the installed TTS engine really offers and plays samples of them. */
class TtsProbe(context: Context, private val report: LabReport) {
    private val _groups = MutableStateFlow<List<AccentGroup>>(emptyList())
    val groups: StateFlow<List<AccentGroup>> = _groups

    private var voicesByTag: Map<String, List<Voice>> = emptyMap()
    private val nextIndex = mutableMapOf<String, Int>()
    private val tts = TextToSpeech(context.applicationContext) { status -> onEngineReady(status) }

    private fun onEngineReady(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            scan()
        } else {
            report.add("TTS", "엔진 초기화 실패 (status=$status)")
        }
    }

    private fun scan() {
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        // Spanish and Hindi voices are kept only for the "foreign voice reads English" experiment.
        val relevant = all.filter { voice -> voice.locale.language in setOf("en", "es", "hi") }
        voicesByTag = relevant
            .groupBy { it.locale.toLanguageTag() }
            // Installed offline voices first, so the first sample shows what works without a network.
            .mapValues { (_, voices) ->
                voices.sortedWith(
                    compareBy({ it.isNotInstalled() }, { it.isNetworkConnectionRequired }, { -it.quality }),
                )
            }
        publish()

        report.add("TTS", "엔진=${tts.defaultEngine}, 전체 음성=${all.size}")
        for (group in _groups.value) {
            report.add(
                "TTS",
                "${group.tag}: 오프라인 ${group.offlineCount}, 온라인 ${group.onlineCount}, " +
                    "미설치 ${group.notInstalledCount}",
            )
        }
    }

    private fun Voice.isNotInstalled() = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features

    /** Rescans after the user returns from the system voice-data screen. */
    fun rescan() = scan()

    private fun publish() {
        _groups.value = voicesByTag
            .map { (tag, voices) ->
                AccentGroup(
                    tag = tag,
                    isEnglish = tag.startsWith("en"),
                    offlineCount = voices.count { !it.isNetworkConnectionRequired && !it.isNotInstalled() },
                    onlineCount = voices.count { it.isNetworkConnectionRequired && !it.isNotInstalled() },
                    notInstalledCount = voices.count { it.isNotInstalled() },
                    nextVoiceName = voices[(nextIndex[tag] ?: 0) % voices.size].name,
                )
            }
            .sortedWith(compareBy({ !it.isEnglish }, { it.tag }))
    }

    /** Plays the sample with the next voice of this locale. */
    fun playNext(tag: String, text: String, rate: Float = 1f) {
        val voices = voicesByTag[tag] ?: return
        val index = (nextIndex[tag] ?: 0) % voices.size
        val voice = voices[index]
        nextIndex[tag] = index + 1
        publish()

        val voiceResult = tts.setVoice(voice)
        tts.setSpeechRate(rate)
        val speakResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lab-$tag-$index")
        report.add(
            "TTS",
            "재생 $tag / ${voice.name} / 온라인필요=${voice.isNetworkConnectionRequired} / " +
                "미설치=${voice.isNotInstalled()} / " +
                "setVoice=$voiceResult speak=$speakResult",
        )
    }

    fun close() {
        tts.stop()
        tts.shutdown()
    }
}
