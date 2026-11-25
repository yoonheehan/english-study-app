package com.grayman.englishstudy.ui.words

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.GoogleAccount
import com.grayman.englishstudy.data.words.SyncStatus
import com.grayman.englishstudy.data.words.PartOfSpeech
import com.grayman.englishstudy.data.words.WordCounts
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.data.words.WordFilter
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SpeechBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** On a tablet in landscape the list and the card sit side by side; on a phone the card opens over the list. */
private val TWO_PANE_MIN_WIDTH = 720.dp

/**
 * Read-only word list: words are added, edited and deleted only from the PC, through Firestore.
 * The app may still change study progress (favorite, remembered, review schedule).
 */
@Composable
fun WordsTab(onStudy: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(WordFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val words by remember(query, filter) { app.words.search(query, filter) }.collectAsState(initial = null)
    val counts by remember { app.words.counts() }.collectAsState(initial = WordCounts(0, 0, 0, 0))
    val syncStatus by app.wordSync.status.collectAsState()
    val syncContent by app.wordSync.contentInfo.collectAsState()
    val uploadProblem by app.wordSync.uploadProblem.collectAsState()
    val syncPending by remember { app.wordSync.pendingCount }.collectAsState(initial = 0)
    var showSync by remember { mutableStateOf(false) }
    val account by app.googleAccount.email.collectAsState()
    var connecting by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
        val list: @Composable (Modifier) -> Unit = { modifier ->
            WordList(
                modifier = modifier,
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { filter = it },
                counts = counts,
                words = words,
                selectedId = if (twoPane) selectedId else null,
                onSelect = { selectedId = it },
                onFavorite = { word -> scope.launch { app.words.setFavorite(word.id, !word.favorite) } },
                syncStatus = syncStatus,
                onSync = { showSync = true },
                onStudy = onStudy,
            )
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(0.42f).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    val id = selectedId
                    if (id == null) {
                        EmptyDetail()
                    } else {
                        WordDetail(id = id, onBack = null)
                    }
                }
            }
        } else {
            val id = selectedId
            if (id == null) {
                list(Modifier.fillMaxSize())
            } else {
                BackHandler { selectedId = null }
                WordDetail(id = id, onBack = { selectedId = null })
            }
        }
    }

    if (showSync) {
        SyncDialog(
            status = syncStatus,
            pending = syncPending,
            contentInfo = syncContent,
            uploadProblem = uploadProblem,
            account = account,
            connecting = connecting,
            accountMessage = accountMessage,
            onConnectGoogle = {
                connecting = true
                accountMessage = null
                scope.launch {
                    accountMessage = when (val result = app.googleAccount.connect(activity)) {
                        GoogleAccount.Result.Linked -> "연결했어요. 이 기기의 UID는 그대로예요."
                        GoogleAccount.Result.Switched -> "연결했어요. 이 계정을 먼저 연결한 기기와 같은 UID를 써요."
                        GoogleAccount.Result.Cancelled -> null
                        is GoogleAccount.Result.Failed -> "연결하지 못했어요: ${result.message}"
                    }
                    connecting = false
                    app.wordSync.start()
                }
            },
            onRetry = { app.wordSync.start() },
            onDismiss = { showSync = false },
        )
    }
}

@Composable
private fun WordList(
    modifier: Modifier,
    query: String,
    onQuery: (String) -> Unit,
    filter: WordFilter,
    onFilter: (WordFilter) -> Unit,
    counts: WordCounts,
    words: List<WordEntity>?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onFavorite: (WordEntity) -> Unit,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
    onStudy: () -> Unit,
) {
    Column(modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("단어장", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "${counts.total}개 · 외운 단어 ${counts.mastered}개",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChunkyButton(
                text = "외우기",
                onClick = onStudy,
                icon = Icons.Rounded.Edit,
                modifier = Modifier.width(132.dp).padding(end = 4.dp),
            )
            IconButton(onClick = onSync) {
                val (icon, tint) = syncIcon(syncStatus)
                Icon(icon, contentDescription = "기기 동기화", tint = tint)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("단어나 뜻으로 찾기") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기") }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordFilter.entries.forEach { option ->
                val count = when (option) {
                    WordFilter.ALL -> counts.total
                    WordFilter.FAVORITE -> counts.favorite
                    WordFilter.DUE -> counts.due
                    WordFilter.LEARNING -> counts.total - counts.mastered
                    WordFilter.MASTERED -> counts.mastered
                }
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text("${option.labelKo} $count") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Game.LagoonSoft,
                        selectedLabelColor = Game.LagoonEdge,
                    ),
                )
            }
        }

        when {
            words == null -> Unit
            words.isEmpty() -> Box(Modifier.padding(16.dp)) {
                SpeechBubble(emptyMessage(filter, query))
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(words, key = { it.id }) { word ->
                    WordRow(word, selected = word.id == selectedId, onClick = { onSelect(word.id) }, onFavorite = { onFavorite(word) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: WordEntity, selected: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Game.LagoonSoft else MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (word.partOfSpeech.isNotEmpty()) {
                    Text(
                        partOfSpeechKo(word.partOfSpeech),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (word.mastery >= 100) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "외운 단어", tint = Game.Good, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                word.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (word.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (word.favorite) "즐겨찾기 해제" else "즐겨찾기",
                tint = if (word.favorite) Game.Flame else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "왼쪽에서 단어를 고르면 뜻과 예문이 여기에 나와요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun emptyMessage(filter: WordFilter, query: String): String = when {
    query.isNotBlank() -> "\"$query\"에 맞는 단어가 없어요. 단어 추가는 PC에서 해 주세요."
    filter == WordFilter.FAVORITE -> "아직 즐겨찾기한 단어가 없어요. 별을 눌러 모아 보세요."
    filter == WordFilter.DUE -> "오늘 복습할 단어가 없어요. 외운 단어는 정해진 날에 여기로 와요."
    filter == WordFilter.MASTERED -> "아직 외운 단어가 없어요."
    else -> "단어를 서버에서 받는 중이에요. 오래 걸리면 오른쪽 위 구름 버튼에서 연결 상태를 확인해 주세요."
}

fun partOfSpeechKo(value: String): String = PartOfSpeech.ko(value)

@Composable
internal fun Spacer8() = Spacer(Modifier.width(8.dp))
