package com.grayman.englishstudy.data.words

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * English to Korean on the device with ML Kit. The model is downloaded once (about 30 MB) and
 * then works offline. It translates rather than defines, so it gives a starting meaning the
 * learner can edit, not a dictionary entry.
 */
class KoreanTranslator {
    enum class ModelState { UNKNOWN, DOWNLOADING, READY, FAILED }

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.KOREAN)
            .build(),
    )
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ModelState.UNKNOWN)
    val state: StateFlow<ModelState> = _state

    /** Downloads the model when missing. Safe to call repeatedly; only one download runs. */
    suspend fun prepare(): Boolean = mutex.withLock {
        if (_state.value == ModelState.READY) return true
        _state.value = ModelState.DOWNLOADING
        val ok = runCatching { translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await() }.isSuccess
        _state.value = if (ok) ModelState.READY else ModelState.FAILED
        ok
    }

    /** Null when the model is not available, so the screen can ask for a meaning instead. */
    suspend fun translate(text: String): String? {
        if (text.isBlank() || !prepare()) return null
        return runCatching { translator.translate(text).await() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
