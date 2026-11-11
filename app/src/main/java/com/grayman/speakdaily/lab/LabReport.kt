package com.grayman.speakdaily.lab

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Collects Phase 0 results so they can be read on screen, copied, or pulled with logcat. */
class LabReport {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun add(section: String, message: String) {
        val line = "[$section] $message"
        Log.i(TAG, line)
        _lines.update { it + line }
    }

    fun text(): String = _lines.value.joinToString("\n")

    private companion object {
        const val TAG = "PHASE0"
    }
}
