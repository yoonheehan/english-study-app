package com.grayman.englishstudy.ui.study

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Reads handwriting with ML Kit, one recognizer each for English and Korean. Each model is
 * downloaded once (about 20 MB) and then works offline.
 */
class InkReader(languageTag: String) {
    enum class State { PREPARING, READY, FAILED }

    private val model = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        ?.let { DigitalInkRecognitionModel.builder(it).build() }
    private val recognizer: DigitalInkRecognizer? =
        model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(State.PREPARING)
    val state: StateFlow<State> = _state

    suspend fun prepare() = mutex.withLock {
        if (_state.value == State.READY) return@withLock
        val m = model ?: run { _state.value = State.FAILED; return@withLock }
        _state.value = State.PREPARING
        _state.value = try {
            val manager = RemoteModelManager.getInstance()
            if (!manager.isModelDownloaded(m).await()) manager.download(m, DownloadConditions.Builder().build()).await()
            State.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "handwriting model download failed", e)
            State.FAILED
        }
    }

    /** Up to five distinct readings, best first; empty when nothing could be read. */
    suspend fun read(ink: Ink): List<String> {
        val r = recognizer ?: return emptyList()
        if (_state.value != State.READY) return emptyList()
        return r.recognize(ink).await().candidates.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
    }

    fun close() = recognizer?.close()

    private companion object {
        const val TAG = "InkReader"
    }
}
