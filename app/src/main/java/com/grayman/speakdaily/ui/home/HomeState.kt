package com.grayman.speakdaily.ui.home

import com.grayman.speakdaily.data.DailyRecordEntity
import com.grayman.speakdaily.data.Lesson
import com.grayman.speakdaily.data.LessonProgressEntity
import com.grayman.speakdaily.data.Level
import com.grayman.speakdaily.data.LevelUnit
import com.grayman.speakdaily.data.ProfileEntity
import com.grayman.speakdaily.domain.Streak

enum class NodeState { DONE, CURRENT, LOCKED }

data class PathNode(val lesson: Lesson, val state: NodeState)

data class PathUnit(val level: Level, val nodes: List<PathNode>)

data class HomeState(
    val level: Level,
    val totalXp: Int,
    val streak: Int,
    val practicedToday: Boolean,
    val path: List<PathUnit>,
    val currentLessonId: String?,
)

/** Builds the lesson path: from the chosen level upward, one open lesson at a time. */
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
                    else -> NodeState.LOCKED
                }
                PathNode(lesson, state)
            },
        )
    }

    val practiced = records.filter { it.xp > 0 }.map { it.epochDay }
    return HomeState(
        level = level,
        totalXp = profile.totalXp,
        streak = Streak.current(practiced, today),
        practicedToday = today in practiced,
        path = path,
        currentLessonId = current,
    )
}
