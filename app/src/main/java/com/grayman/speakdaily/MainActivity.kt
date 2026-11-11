package com.grayman.speakdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.grayman.speakdaily.lab.LabScreen
import com.grayman.speakdaily.ui.SpeakDailyTheme
import com.grayman.speakdaily.ui.home.AppHome
import com.grayman.speakdaily.ui.lesson.LessonScreen
import com.grayman.speakdaily.ui.listening.ListeningTestScreen
import com.grayman.speakdaily.ui.listening.ListeningTestSettings
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data class LessonRoute(val lessonId: String) : NavKey

@Serializable
private data object LabRoute : NavKey

@Serializable
private data class ListeningTestRoute(val settings: ListeningTestSettings) : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Starts the speech engine early, so the first lesson's audio does not lag.
        app.voice
        setContent {
            SpeakDailyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val backStack = rememberNavBackStack(HomeRoute)
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<HomeRoute> {
                                AppHome(
                                    onOpenLesson = { backStack.add(LessonRoute(it)) },
                                    onStartListeningTest = { backStack.add(ListeningTestRoute(it)) },
                                    onOpenLab = { backStack.add(LabRoute) },
                                )
                            }
                            entry<LessonRoute> { route ->
                                LessonScreen(route.lessonId, onExit = { backStack.removeLastOrNull() })
                            }
                            entry<LabRoute> { LabScreen() }
                            entry<ListeningTestRoute> { route ->
                                ListeningTestScreen(
                                    settings = route.settings,
                                    onExit = { backStack.removeLastOrNull() },
                                    onRetry = {
                                        backStack.removeLastOrNull()
                                        backStack.add(ListeningTestRoute(route.settings.copy(attempt = route.settings.attempt + 1)))
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
