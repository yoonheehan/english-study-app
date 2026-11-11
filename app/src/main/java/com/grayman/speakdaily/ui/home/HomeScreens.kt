package com.grayman.speakdaily.ui.home

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.grayman.speakdaily.app
import com.grayman.speakdaily.data.DailyRecordEntity
import com.grayman.speakdaily.data.Level
import com.grayman.speakdaily.data.ProfileEntity
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Chip
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.Mascot
import com.grayman.speakdaily.ui.MascotMood
import com.grayman.speakdaily.ui.SecondaryButton
import com.grayman.speakdaily.ui.SpeechBubble
import com.grayman.speakdaily.ui.listening.ListeningTestSettings
import com.grayman.speakdaily.ui.listening.ListeningTestTab
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class LoadedProfile(val profile: ProfileEntity?)

@Composable
fun AppHome(
    onOpenLesson: (String) -> Unit,
    onStartListeningTest: (ListeningTestSettings) -> Unit,
    onOpenLab: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    // Wrapped so "still loading" differs from "no profile yet"; otherwise returning
    // learners would see the first-run screen flash before their profile arrives.
    val loadedProfile by remember { app.progress.profile.map { LoadedProfile(it) } }.collectAsState(initial = null)
    val progress by app.progress.progress.collectAsState(initial = emptyList())
    val records by app.progress.dailyRecords.collectAsState(initial = emptyList())
    val dueCount by remember { app.progress.dueCount() }.collectAsState(initial = 0)
    var tab by rememberSaveable { mutableStateOf(0) }

    val loaded = loadedProfile ?: return
    val current = loaded.profile
    if (current == null) {
        Onboarding { level -> scope.launch { app.progress.setLevel(level) } }
        return
    }

    val state = remember(current, progress, records) {
        buildHomeState(current, app.content.units, progress, records, app.progress.today())
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            BottomTabs(tab) { tab = it }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) {
                PathScreen(state, dueCount, onOpenLesson)
            } else if (tab == 1) {
                ListeningTestTab(state.level, onStartListeningTest)
            } else {
                RecordsScreen(
                    state = state,
                    records = records,
                    lessonsDone = progress.count { it.completions > 0 },
                    dueCount = dueCount,
                    onChangeLevel = { level -> scope.launch { app.progress.setLevel(level) } },
                    onOpenLab = onOpenLab,
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabButton("학습", Icons.Rounded.Route, selected == 0, Modifier.weight(1f)) { onSelect(0) }
            TabButton("듣기 테스트", Icons.Rounded.Headphones, selected == 1, Modifier.weight(1.3f)) { onSelect(1) }
            TabButton("기록", Icons.AutoMirrored.Rounded.ShowChart, selected == 2, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun TabButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Game.LagoonSoft else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Onboarding(onChoose: (Level) -> Unit) {
    var selected by remember { mutableStateOf<Level?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotMood.IDLE, size = 96.dp)
            Spacer(Modifier.width(8.dp))
            SpeechBubble("안녕하세요, 저는 에코예요! 지금 영어 실력은 어느 정도인가요?", Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Level.entries.forEach { level ->
            val isSelected = selected == level
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Game.LagoonSoft else MaterialTheme.colorScheme.surface)
                    .border(
                        2.dp,
                        if (isSelected) Game.Lagoon else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(role = Role.RadioButton) { selected = level }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    level.code.uppercase(),
                    if (isSelected) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant,
                    if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        level.labelKo,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        level.descriptionKo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        ChunkyButton(
            "시작하기",
            onClick = { selected?.let(onChoose) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PathScreen(state: HomeState, dueCount: Int, onOpenLesson: (String) -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopStats(state)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TodayBanner(state, dueCount)
            }
            state.path.forEach { unit ->
                item(key = "unit-${unit.level.code}") { UnitHeader(unit) }
                val nodes = unit.nodes
                items(nodes, key = { it.lesson.id }) { node ->
                    val index = nodes.indexOf(node)
                    PathNodeRow(node, index) { onOpenLesson(node.lesson.id) }
                }
            }
            if (state.currentLessonId == null) {
                item {
                    SpeechBubble("지금 있는 레슨을 전부 끝냈어요! 다시 연습하거나 새 콘텐츠를 기다려 주세요.")
                }
            }
        }
    }
}

@Composable
private fun TopStats(state: HomeState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Chip(
            "${state.level.code.uppercase()} ${state.level.labelKo}",
            Game.LagoonSoft,
            Game.LagoonEdge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat(Icons.Rounded.LocalFireDepartment, Game.Flame, "${state.streak}일")
            Stat(Icons.Rounded.Bolt, Game.Xp, "${state.totalXp} XP")
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, color = tint, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TodayBanner(state: HomeState, dueCount: Int) {
    val message = when {
        state.practicedToday -> "오늘 연습 완료! 더 하고 싶다면 언제든 환영이에요."
        dueCount > 0 -> "복습할 표현 ${dueCount}개가 기다리고 있어요. 오늘도 10분만 같이 말해 봐요!"
        state.streak > 0 -> "${state.streak}일째 이어 가는 중이에요. 오늘도 해 볼까요?"
        else -> "오늘의 첫 레슨을 시작해 봐요. 10분이면 충분해요!"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(if (state.practicedToday) MascotMood.HAPPY else MascotMood.IDLE, size = 76.dp)
        Spacer(Modifier.width(8.dp))
        SpeechBubble(message, Modifier.weight(1f))
    }
}

@Composable
private fun UnitHeader(unit: PathUnit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Game.Lagoon, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "${unit.level.code.uppercase()} · ${unit.level.labelKo}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(unit.level.descriptionKo, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The path winds left and right, so progress reads as a journey rather than a list. */
@Composable
private fun PathNodeRow(node: PathNode, index: Int, onClick: () -> Unit) {
    val shift = listOf(-56, 0, 56, 0)[index % 4].dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.offset(x = shift), horizontalAlignment = Alignment.CenterHorizontally) {
            LessonBubble(node, onClick)
            Spacer(Modifier.height(4.dp))
            Text(
                node.lesson.titleKo,
                style = MaterialTheme.typography.labelMedium,
                color = if (node.state == NodeState.LOCKED) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun LessonBubble(node: PathNode, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "node").animateFloat(
        1f,
        1.12f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val (face, edge) = when (node.state) {
        NodeState.DONE -> Game.Good to Game.GoodEdge
        NodeState.CURRENT -> Game.Lagoon to Game.LagoonEdge
        NodeState.LOCKED -> Game.Locked to Color(0xFF99A3AB)
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
        if (node.state == NodeState.CURRENT) {
            Box(
                Modifier
                    .size(84.dp)
                    .scale(pulse.value)
                    .border(5.dp, Game.Lagoon.copy(alpha = 0.35f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(70.dp)
                .offset(y = 4.dp)
                .background(edge, CircleShape),
        )
        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(face)
                .clickable(enabled = node.state != NodeState.LOCKED, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (node.state) {
                    NodeState.DONE -> Icons.Rounded.Check
                    NodeState.LOCKED -> Icons.Rounded.Lock
                    NodeState.CURRENT -> iconFor(node.lesson.icon)
                },
                contentDescription = node.lesson.titleKo,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private fun iconFor(name: String): ImageVector = when (name) {
    "coffee" -> Icons.Rounded.Coffee
    "map" -> Icons.Rounded.Map
    "bed" -> Icons.Rounded.Bed
    "calendar" -> Icons.Rounded.CalendarMonth
    "bag" -> Icons.Rounded.ShoppingBag
    "plane" -> Icons.Rounded.Flight
    "health" -> Icons.Rounded.LocalHospital
    "chat" -> Icons.AutoMirrored.Rounded.Chat
    "work" -> Icons.Rounded.Work
    "home" -> Icons.Rounded.Home
    "clock" -> Icons.Rounded.Schedule
    "money" -> Icons.Rounded.AccountBalanceWallet
    "forum" -> Icons.Rounded.Forum
    "feedback" -> Icons.Rounded.Feedback
    else -> Icons.Rounded.WavingHand
}

@Composable
private fun RecordsScreen(
    state: HomeState,
    records: List<DailyRecordEntity>,
    lessonsDone: Int,
    dueCount: Int,
    onChangeLevel: (Level) -> Unit,
    onOpenLab: () -> Unit,
) {
    val context = LocalContext.current
    val voice = context.app.voice
    val accents by voice.accents.collectAsState()
    val notInstalled by voice.notInstalled.collectAsState()
    LifecycleResumeEffect(Unit) {
        voice.refreshVoices()
        onPauseOrDispose { }
    }
    val totalSeconds = records.sumOf { it.speakingSeconds }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("내 기록", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("연속 학습", "${state.streak}일", Game.Flame, Modifier.weight(1f))
            StatCard("총 XP", "${state.totalXp}", Game.Xp, Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("말한 시간", formatSpeaking(totalSeconds), Game.Coral, Modifier.weight(1f))
            StatCard("끝낸 레슨", "${lessonsDone}개", Game.Good, Modifier.weight(1f))
        }
        if (dueCount > 0) SpeechBubble("오늘 복습할 표현이 ${dueCount}개 있어요. 다음 레슨 첫 단계에 나와요.")

        WeekStrip(records, context.app.progress.today())

        SectionTitle("시작 레벨")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Level.entries.forEach { level ->
                val isCurrent = level == state.level
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.RadioButton) { onChangeLevel(level) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        level.code.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("억양 음성")
        Text(
            "사용 중: " + accents.joinToString(", ") { it.labelKo }.ifEmpty { "확인 중" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (notInstalled.isNotEmpty()) {
            Text(
                "받으면 더 들을 수 있어요: " + notInstalled.joinToString(", ") { it.labelKo },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryButton(
            "억양 음성 설치",
            onClick = {
                runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("개발자")
        SecondaryButton("기능 검증 랩 열기", onClick = onOpenLab, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
    }
}

@Composable
private fun WeekStrip(records: List<DailyRecordEntity>, today: Long) {
    val practiced = records.filter { it.xp > 0 }.map { it.epochDay }.toSet()
    val labels = listOf("월", "화", "수", "목", "금", "토", "일")
    val todayDate = java.time.LocalDate.ofEpochDay(today)
    val monday = today - (todayDate.dayOfWeek.value - 1)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            val day = monday + index
            val done = day in practiced
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .background(if (done) Game.Flame else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.LocalFireDepartment, "연습함", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatSpeaking(seconds: Int): String =
    if (seconds < 60) "${seconds}초" else "${seconds / 60}분"
