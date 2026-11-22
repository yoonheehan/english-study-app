package com.grayman.englishstudy.ui.home

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
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.DailyRecordEntity
import com.grayman.englishstudy.data.Level
import com.grayman.englishstudy.data.ProfileEntity
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Chip
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.Mascot
import com.grayman.englishstudy.ui.MascotMood
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.SoundTestCard
import com.grayman.englishstudy.ui.SpeechBubble
import com.grayman.englishstudy.ui.listening.ListeningTestSettings
import com.grayman.englishstudy.ui.listening.ListeningTestTab
import com.grayman.englishstudy.ui.roleplay.RolePlayTab
import com.grayman.englishstudy.ui.words.WordsTab
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class LoadedProfile(val profile: ProfileEntity?)

@Composable
fun AppHome(
    onOpenLesson: (String) -> Unit,
    onStartListeningTest: (ListeningTestSettings) -> Unit,
    onOpenLab: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenRolePlay: (String, String?) -> Unit,
    onOpenToeic: (com.grayman.englishstudy.domain.ToeicPart?, com.grayman.englishstudy.domain.ToeicLevel) -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
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

    // Before the first-run screen: a new install that connects first gets its level and records
    // from the other device instead of starting over.
    GoogleConnectPrompt()
    val loaded = loadedProfile ?: return
    val current = loaded.profile
    if (current == null) {
        Onboarding { level -> scope.launch { app.progress.setLevel(level) } }
        return
    }

    val state = remember(current, progress, records) {
        buildHomeState(current, app.content.units, progress, records, app.progress.today())
    }
    val tabs = listOf(
        Triple("스피킹", Icons.AutoMirrored.Rounded.Chat, 0),
        Triple("말하기", Icons.Rounded.RecordVoiceOver, 1),
        Triple("단어장", Icons.AutoMirrored.Rounded.MenuBook, 2),
        Triple("듣기", Icons.Rounded.Headphones, 3),
        Triple("기록", Icons.AutoMirrored.Rounded.ShowChart, 4),
    )
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Game.LagoonEdge,
            selectedTextColor = Game.LagoonEdge,
            indicatorColor = Game.LagoonSoft,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Game.LagoonEdge,
            selectedTextColor = Game.LagoonEdge,
            indicatorColor = Game.LagoonSoft,
        ),
    )
    // Phones get a bottom bar; tablets and other wide windows get a rail at the edge (adaptive skill, step 2).
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { (label, icon, index) ->
                item(
                    selected = tab == index,
                    onClick = { tab = index },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label) },
                    colors = itemColors,
                )
            }
        },
        layoutType = navigationLayout(),
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
            navigationRailContainerColor = MaterialTheme.colorScheme.surface,
        ),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> PathScreen(state, dueCount, onOpenLesson, onOpenToeic)
                1 -> RolePlayTab(onOpen = onOpenRolePlay)
                2 -> WordsHome(onStudy = onOpenStudy, onOpenStory = onOpenStory, onOpenVideo = onOpenVideo)
                3 -> ListeningTestTab(state.listeningLevel, { level -> scope.launch { app.progress.setListeningLevel(level) } }, onStartListeningTest)
                else -> {
                RecordsScreen(
                    state = state,
                    records = records,
                    lessonsDone = progress.count { it.completions > 0 },
                    dueCount = dueCount,
                    onChangeLevel = { level -> scope.launch { app.progress.setLevel(level) } },
                    onChangeListeningLevel = { level -> scope.launch { app.progress.setListeningLevel(level) } },
                    onOpenLab = onOpenLab,
                )
                }
            }
        }
    }
}

/**
 * A bottom bar on phones and on portrait tablets (the tablet is often held upright, and a bar is
 * familiar there); a rail at the side only when the window is wide and landscape, where height is short.
 */
@Composable
private fun navigationLayout(): NavigationSuiteType {
    val configuration = LocalConfiguration.current
    return if (configuration.screenWidthDp >= 840 && configuration.screenWidthDp > configuration.screenHeightDp) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }
}

/**
 * A new install signs in anonymously, and Firestore refuses that account. When that happens and no
 * Google account is connected, this first tries a silent Google sign-in (works when the account
 * already allowed this app, e.g. after a reinstall), and otherwise asks the learner once per launch.
 */
@Composable
private fun GoogleConnectPrompt() {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val status by app.wordSync.status.collectAsState()
    val account by app.googleAccount.email.collectAsState()
    val notAllowed = status is com.grayman.englishstudy.data.words.SyncStatus.NotAllowed
    var triedSilent by rememberSaveable { mutableStateOf(false) }
    var ask by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notAllowed, account) {
        if (!notAllowed || triedSilent) return@LaunchedEffect
        triedSilent = true
        if (account != null) {
            // Signed in with Google but still refused: the rules do not list this account.
            message = "구글 계정($account)으로 연결돼 있지만 Firestore가 막고 있어요. Firestore 규칙에 이 Gmail이 있는지, 규칙을 게시했는지 확인해 주세요."
            ask = true
            return@LaunchedEffect
        }
        when (app.googleAccount.connectSilently(context)) {
            com.grayman.englishstudy.data.words.GoogleAccount.Result.Linked,
            com.grayman.englishstudy.data.words.GoogleAccount.Result.Switched -> app.wordSync.start()
            else -> ask = true
        }
    }
    if (!ask) return
    AlertDialog(
        onDismissRequest = { if (!working) ask = false },
        title = { Text("구글 계정으로 연결할까요?") },
        text = {
            Text(
                message ?: "이 기기는 새로 설치돼서 아직 단어장과 기록을 동기화할 수 없어요. 다른 기기에서 쓰는 구글 계정으로 연결하면 바로 같은 단어장과 기록을 받아요.",
            )
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                working = true
                scope.launch {
                    when (val r = app.googleAccount.connect(context)) {
                        com.grayman.englishstudy.data.words.GoogleAccount.Result.Linked,
                        com.grayman.englishstudy.data.words.GoogleAccount.Result.Switched -> {
                            app.wordSync.start()
                            ask = false
                        }
                        com.grayman.englishstudy.data.words.GoogleAccount.Result.Cancelled -> Unit
                        is com.grayman.englishstudy.data.words.GoogleAccount.Result.Failed -> message = "연결하지 못했어요: ${r.message}"
                    }
                    working = false
                }
            }) { Text(if (working) "연결하는 중…" else "구글 계정으로 연결") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = { ask = false }) { Text("나중에") } },
    )
}

/** The word book tab: my words, or reading stories and watching videos to collect new ones. */
@Composable
private fun WordsHome(onStudy: () -> Unit, onOpenStory: (String) -> Unit, onOpenVideo: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        SectionToggle(
            listOf("내 단어장" to "검색, 외우기, 복습", "소설 읽기" to "읽다가 단어 넣기", "영상 보기" to "자막에서 단어 넣기"),
            section,
        ) { section = it }
        when (section) {
            0 -> WordsTab(onStudy = onStudy)
            1 -> com.grayman.englishstudy.ui.reader.StoryShelf(onOpenStory, Modifier.fillMaxSize())
            else -> com.grayman.englishstudy.ui.video.VideoShelf(onOpenVideo, Modifier.fillMaxSize())
        }
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
private fun PathScreen(
    state: HomeState,
    dueCount: Int,
    onOpenLesson: (String) -> Unit,
    onOpenToeic: (com.grayman.englishstudy.domain.ToeicPart?, com.grayman.englishstudy.domain.ToeicLevel) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopStats(state)
        SpeakingSectionToggle(section) { section = it }
        if (section == 1) {
            com.grayman.englishstudy.ui.toeic.ToeicHome(onOpenToeic, Modifier.fillMaxSize())
            return@Column
        }
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
                    SpeechBubble("모든 레슨을 한 번씩 해 봤어요! 아무 레슨이나 눌러 계속 연습해요.")
                }
            }
        }
    }
}

/** Lessons (repeat and respond) or TOEIC Speaking practice, inside the speaking tab. */
@Composable
private fun SpeakingSectionToggle(selected: Int, onSelect: (Int) -> Unit) =
    SectionToggle(listOf("레슨" to "듣고 따라 말하고, 상황에 맞게 대답하기", "토익 스피킹" to "실전 형식 모의고사와 파트별 연습"), selected, onSelect)

@Composable
private fun SectionToggle(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (title, sub) ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (on) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
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
        dueCount > 0 -> "복습할 표현 ${dueCount}개가 기다리고 있어요. 오늘도 10분만 말해 봐요!"
        state.streak > 0 -> "${state.streak}일째 이어 가는 중이에요. 오늘도 해 볼까요?"
        else -> "오늘의 첫 레슨을 시작해 봐요. 10분이면 충분해요!"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (state.practicedToday) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
    val status = when (node.state) {
        NodeState.DONE -> "완료"
        NodeState.CURRENT -> "다음 레슨"
        NodeState.OPEN -> "연습하기"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .offset(x = shift)
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { stateDescription = status }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LessonIcon(node)
            Spacer(Modifier.height(4.dp))
            Text(
                node.lesson.titleKo,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LessonIcon(node: PathNode) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val current = node.state == NodeState.CURRENT
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(if (current) Game.LagoonSoft else colors.surfaceVariant, shape)
            .border(1.dp, if (current) Game.LagoonEdge else colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            iconFor(node.lesson.icon),
            contentDescription = null,
            tint = if (current) Game.LagoonEdge else colors.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (node.state == NodeState.DONE) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Game.OnGoodSoft,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .background(Game.GoodSoft, CircleShape)
                    .padding(3.dp),
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
    onChangeListeningLevel: (Level) -> Unit,
    onOpenLab: () -> Unit,
) {
    val context = LocalContext.current
    val voice = context.app.voice
    var showCalendar by remember { mutableStateOf(false) }
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

        WeekStrip(records, context.app.progress.today(), onOpen = { showCalendar = true })

        SectionTitle("스피킹 레벨")
        LevelRow(state.level, onChangeLevel)
        SectionTitle("듣기 레벨")
        LevelRow(state.listeningLevel, onChangeListeningLevel)
        Text(
            "스피킹(레슨)과 듣기는 레벨이 따로예요. 한쪽을 올려도 다른 쪽은 그대로예요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ActivityHistory()
        SyncAllCard()
        SoundTestCard()

        SectionTitle("억양 음성")
        Text(
            "듣기·레슨·말하기에서 쓸 억양을 고르세요. '섞기'는 날마다, 문제마다 억양이 바뀌어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccentPicker(Modifier.fillMaxWidth())
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
                runCatching { context.startActivity(context.app.voice.installVoicesIntent()) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showCalendar) {
        StudyCalendarDialog(records, context.app.progress.today(), onDismiss = { showCalendar = false })
    }
}

@Composable
private fun LevelRow(current: Level, onChange: (Level) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Level.entries.forEach { level ->
            val isCurrent = level == current
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.RadioButton) { onChange(level) },
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
private fun WeekStrip(records: List<DailyRecordEntity>, today: Long, onOpen: () -> Unit) {
    val practiced = records.filter { it.xp > 0 }.map { it.epochDay }.toSet()
    val labels = listOf("월", "화", "수", "목", "금", "토", "일")
    val todayDate = java.time.LocalDate.ofEpochDay(today)
    val monday = today - (todayDate.dayOfWeek.value - 1)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "학습 달력 열기", onClick = onOpen)
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
