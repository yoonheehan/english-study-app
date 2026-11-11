package com.grayman.speakdaily

import android.app.Application
import android.content.Context
import com.grayman.speakdaily.data.AppDatabase
import com.grayman.speakdaily.data.ContentRepository
import com.grayman.speakdaily.data.ListeningRepository
import com.grayman.speakdaily.data.ProgressRepository
import com.grayman.speakdaily.speech.SpeechInput
import com.grayman.speakdaily.speech.VoiceOutput

/** Manual dependency container: one personal app does not need a DI framework. */
class SpeakDailyApp : Application() {
    val content by lazy { ContentRepository(this) }
    val listening by lazy { ListeningRepository(this) }
    val progress by lazy { ProgressRepository(AppDatabase.create(this).dao()) }
    val voice by lazy { VoiceOutput(this) }
    val speech by lazy { SpeechInput(this) }
}

val Context.app: SpeakDailyApp get() = applicationContext as SpeakDailyApp
