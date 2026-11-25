package com.grayman.englishstudy

import android.app.Application
import android.content.Context
import com.grayman.englishstudy.ai.GemmaTutor
import com.grayman.englishstudy.data.AppDatabase
import com.grayman.englishstudy.data.ContentRepository
import com.grayman.englishstudy.data.ListeningRepository
import com.grayman.englishstudy.data.ProgressRepository
import com.grayman.englishstudy.data.ProgressSync
import com.grayman.englishstudy.data.words.GoogleAccount
import com.grayman.englishstudy.data.words.KoreanTranslator
import com.grayman.englishstudy.data.words.SyncStatus
import com.grayman.englishstudy.data.words.WordRepository
import com.grayman.englishstudy.data.words.WordSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.grayman.englishstudy.speech.SpeechInput
import com.grayman.englishstudy.speech.VoiceOutput

/** Manual dependency container: one personal app does not need a DI framework. */
class SpeakDailyApp : Application() {
    val content by lazy { ContentRepository(this) }
    val listening by lazy { ListeningRepository(this) }
    private val database by lazy { AppDatabase.create(this) }
    val progress: ProgressRepository by lazy { ProgressRepository(database.dao()) { progressSync.requestSync() } }
    val words by lazy { WordRepository(database.words()) }
    val wordSync by lazy { WordSync(this, database.words(), appScope) }
    val googleAccount by lazy { GoogleAccount() }
    val progressSync: ProgressSync by lazy { ProgressSync(this, { progress }, appScope) }
    val tutor by lazy { GemmaTutor(this) }
    val translator by lazy { KoreanTranslator() }
    val voice by lazy { VoiceOutput(this) }
    val speech by lazy { SpeechInput(this) }
    val stories by lazy { com.grayman.englishstudy.data.StoryLibrary(this) }
    val videos by lazy { com.grayman.englishstudy.data.video.YouTubeCaptions(this) }
    val videoSearch by lazy { com.grayman.englishstudy.data.video.YouTubeSearch() }

    /** Lives as long as the process, for work that must outlast any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        wordSync.start()
        // Once this device is allowed to sync, bring the study records up to date in both directions.
        // Each time sync connects (at start, or after switching accounts), bring the study
        // records up to date in both directions.
        appScope.launch {
            wordSync.status.map { it as? SyncStatus.Connected }.distinctUntilChanged().filterNotNull().collect {
                runCatching { progressSync.syncAll() }
            }
        }
    }
}

val Context.app: SpeakDailyApp get() = applicationContext as SpeakDailyApp
