package com.grayman.englishstudy.ui.home

import com.grayman.englishstudy.data.DailyRecordEntity
import com.grayman.englishstudy.data.Lesson
import com.grayman.englishstudy.data.LessonProgressEntity
import com.grayman.englishstudy.data.Level
import com.grayman.englishstudy.data.LevelUnit
import com.grayman.englishstudy.data.ProfileEntity
import com.grayman.englishstudy.domain.Streak

/** Every lesson is open: this is endless practice, so nothing waits behind a lock. */
enum class NodeState { DONE, CURRENT, OPEN }

data class PathNode(val lesson: Lesson, val state: NodeState)

data class PathUnit(val level: Level, val nodes: List<PathNode>)

data class HomeState(
    val level: Level,
    val listeningLevel: Level,
    val totalXp: Int,
    val streak: Int,
    val practicedToday: Boolean,
    val path: List<PathUnit>,
    val currentLessonId: String?,
)

/** Builds the lesson path from the chosen level upward; CURRENT marks the first lesson not yet done. */
fun buildHomeState(
    profile: ProfileEntity,
    units: List<LevelUnit>,
    progress: List<LessonProgressEntity>,
    records: List<DailyRecordEntity>,
    today: Long,
): HomeState {
    val level = Level.fromCode(profile.levelCode)
    val done = progress.filter { it.completions > 0 }.map { it.lessonId }.toSet()
    val visible = units.filter { it.level.ordinal >= level.ordinal }
    val current = visible.flatMap { it.lessons }.firstOrNull { it.id !in done }?.id

    val path = visible.map { unit ->
        PathUnit(
            unit.level,
            unit.lessons.map { lesson ->
                val state = when (lesson.id) {
                    in done -> NodeState.DONE
                    current -> NodeState.CURRENT
                    else -> NodeState.OPEN
                }
                PathNode(lesson, state)
            },
        )
    }

    val practiced = records.filter { it.xp > 0 }.map { it.epochDay }
    return HomeState(
        level = level,
        listeningLevel = Level.fromCode(profile.listeningLevelCode ?: profile.levelCode),
        totalXp = profile.totalXp,
        streak = Streak.current(practiced, today),
        practicedToday = today in practiced,
        path = path,
        currentLessonId = current,
    )
}
