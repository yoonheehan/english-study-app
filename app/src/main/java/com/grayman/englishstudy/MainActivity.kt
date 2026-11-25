package com.grayman.englishstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.grayman.englishstudy.domain.StudySettings
import com.grayman.englishstudy.domain.ToeicLevel
import com.grayman.englishstudy.domain.ToeicPart
import com.grayman.englishstudy.ui.toeic.ToeicScreen
import com.grayman.englishstudy.lab.LabScreen
import com.grayman.englishstudy.ui.SpeakDailyTheme
import com.grayman.englishstudy.ui.home.AppHome
import com.grayman.englishstudy.ui.lesson.LessonScreen
import com.grayman.englishstudy.ui.listening.ListeningTestScreen
import com.grayman.englishstudy.ui.listening.ListeningTestSettings
import com.grayman.englishstudy.ui.roleplay.RolePlayScreen
import com.grayman.englishstudy.ui.study.StudySession
import com.grayman.englishstudy.ui.study.StudySetup
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data class LessonRoute(val lessonId: String) : NavKey

@Serializable
private data object LabRoute : NavKey

@Serializable
private data class ListeningTestRoute(val settings: ListeningTestSettings) : NavKey

@Serializable
private data class RolePlayRoute(val topicId: String, val custom: String? = null, val attempt: Int = 0) : NavKey

@Serializable
private data object StudySetupRoute : NavKey

@Serializable
private data class StoryRoute(val storyId: String) : NavKey

@Serializable
private data class ChapterRoute(val storyId: String, val chapter: Int) : NavKey

@Serializable
private data class VideoRoute(val videoId: String) : NavKey

@Serializable
private data class ToeicRoute(val part: ToeicPart?, val level: ToeicLevel, val attempt: Int = 0) : NavKey

/** `attempt` makes "N개 더 하기" a new screen, so it draws a fresh random set. */
@Serializable
private data class StudyRoute(val settings: StudySettings, val attempt: Int = 0) : NavKey

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
                                    onOpenStudy = { backStack.add(StudySetupRoute) },
                                    onOpenToeic = { part, level -> backStack.add(ToeicRoute(part, level)) },
                                    onOpenRolePlay = { id, custom -> backStack.add(RolePlayRoute(id, custom)) },
                                    onOpenStory = { backStack.add(StoryRoute(it)) },
                                    onOpenVideo = { backStack.add(VideoRoute(it)) },
                                )
                            }
                            entry<VideoRoute> { route ->
                                com.grayman.englishstudy.ui.video.VideoStudyScreen(route.videoId, onExit = { backStack.removeLastOrNull() })
                            }
                            entry<StoryRoute> { route ->
                                com.grayman.englishstudy.ui.reader.StoryChaptersScreen(
                                    route.storyId,
                                    onOpenChapter = { backStack.add(ChapterRoute(route.storyId, it)) },
                                    onExit = { backStack.removeLastOrNull() },
                                )
                            }
                            entry<ChapterRoute> { route ->
                                com.grayman.englishstudy.ui.reader.StoryReaderScreen(
                                    route.storyId,
                                    route.chapter,
                                    onExit = { backStack.removeLastOrNull() },
                                    // The next chapter replaces this one, so "back" returns to the chapter list.
                                    onOpenChapter = {
                                        backStack.removeLastOrNull()
                                        backStack.add(ChapterRoute(route.storyId, it))
                                    },
                                )
                            }
                            entry<LessonRoute> { route ->
                                LessonScreen(route.lessonId, onExit = { backStack.removeLastOrNull() })
                            }
                            entry<LabRoute> { LabScreen() }
                            entry<RolePlayRoute> { route ->
                                RolePlayScreen(
                                    route.topicId,
                                    route.custom,
                                    onExit = { backStack.removeLastOrNull() },
                                    onRestart = {
                                        backStack.removeLastOrNull()
                                        backStack.add(route.copy(attempt = route.attempt + 1))
                                    },
                                )
                            }
                            entry<ToeicRoute> { route ->
                                ToeicScreen(
                                    route.part,
                                    route.level,
                                    onExit = { backStack.removeLastOrNull() },
                                    onAgain = {
                                        backStack.removeLastOrNull()
                                        backStack.add(route.copy(attempt = route.attempt + 1))
                                    },
                                )
                            }
                            entry<StudySetupRoute> {
                                StudySetup(
                                    onStart = { backStack.add(StudyRoute(it)) },
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                            entry<StudyRoute> { route ->
                                StudySession(
                                    settings = route.settings,
                                    // Back to the word tab, past the setup screen.
                                    onExit = {
                                        backStack.removeLastOrNull()
                                        if (backStack.lastOrNull() == StudySetupRoute) backStack.removeLastOrNull()
                                    },
                                    onAgain = {
                                        backStack.removeLastOrNull()
                                        backStack.add(route.copy(attempt = route.attempt + 1))
                                    },
                                )
                            }
                            entry<ListeningTestRoute> { route ->
                                ListeningTestScreen(
                                    settings = route.settings,
                                    onExit = { backStack.removeLastOrNull() },
                                    onRetry = { level ->
                                        backStack.removeLastOrNull()
                                        val settings = route.settings.copy(attempt = route.settings.attempt + 1)
                                        backStack.add(ListeningTestRoute(level?.let { settings.copy(levelCode = it.code) } ?: settings))
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
