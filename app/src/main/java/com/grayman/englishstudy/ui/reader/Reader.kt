package com.grayman.englishstudy.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.StoryInfo
import com.grayman.englishstudy.data.StoryLevel
import com.grayman.englishstudy.data.StoryText
import com.grayman.englishstudy.data.words.WordDuplicates
import com.grayman.englishstudy.data.words.WordMaker
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.GameProgressBar
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.words.WordAddCard
import kotlinx.coroutines.withTimeoutOrNull

internal val SavedHighlight = Color(0xFFFFE9A8)

/** The spellings to highlight for saved cards: the card's word and the form met in its example. */
internal fun savedFormsOf(saved: List<com.grayman.englishstudy.data.words.WordEntity>): Set<String> =
    saved.flatMap { w ->
        listOf(w.word) + Regex("[A-Za-z’'-]+").findAll(w.example1).map { it.value }
            .filter { WordDuplicates.normalize(it).startsWith(WordDuplicates.normalize(w.word).take(4)) }.toList()
    }.map { WordDuplicates.normalize(it) }.toSet()

/** The list of stories, inside the word book tab. Cards flow into as many columns as fit. */
@Composable
fun StoryShelf(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val stories = remember { app.stories.index.stories }
    val priority by remember { app.words.priorityCount() }.collectAsState(initial = 0)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        state = rememberLazyGridState(),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().background(Game.LagoonSoft, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("소설 읽으며 단어 모으기", style = MaterialTheme.typography.titleLarge, color = Game.LagoonEdge, fontWeight = FontWeight.Bold)
                Text(
                    "이야기를 고르고 한 장씩 읽어요. 모르는 단어를 누르면 이 문장에서의 뜻이 나오고, 바로 단어장에 넣을 수 있어요. 문단을 길게 누르면 번역돼요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (priority > 0) {
                    Text("읽다가 넣은 단어 ${priority}개가 다음 '새 단어' 외우기에서 먼저 나와요.", style = MaterialTheme.typography.labelLarge, color = Game.LagoonEdge)
                }
            }
        }
        StoryLevel.entries.forEach { level ->
            val inLevel = stories.filter { it.storyLevel == level }
            if (inLevel.isEmpty()) return@forEach
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-${level.name}") {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(level.labelKo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(level.guideKo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(inLevel, key = { it.id }) { story -> StoryCard(story) { onOpen(story.id) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "모두 저작권이 끝난 퍼블릭 도메인 작품이에요 (원문: Project Gutenberg).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryCard(story: StoryInfo, onClick: () -> Unit) {
    val app = LocalContext.current.app
    val percent = remember(story.id) { app.stories.progress(story) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(story.titleKo, style = MaterialTheme.typography.titleMedium, color = Game.LagoonEdge)
        Text(story.title, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif)
        Text("${story.author} · ${story.year}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${story.chapters}장 · 약 ${story.minutes}분 · ${story.words}단어", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (story.note.isNotEmpty()) Text(story.note, style = MaterialTheme.typography.bodySmall)
        if (percent > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameProgressBar(percent / 100f, Modifier.weight(1f), height = 8.dp)
                Text("$percent%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A tapped word: which paragraph, where in it, and the sentence around it. */
private data class Pick(val block: Int, val start: Int, val end: Int, val word: String, val sentence: String)

private val PickSaver = Saver<Pick?, List<Any>>(
    save = { p -> p?.let { listOf(it.block, it.start, it.end, it.word, it.sentence) } ?: emptyList() },
    restore = { v -> if (v.size == 5) Pick(v[0] as Int, v[1] as Int, v[2] as Int, v[3] as String, v[4] as String) else null },
)

/** A story's chapters: pick one, or continue where reading stopped. */
@Composable
fun StoryChaptersScreen(storyId: String, onOpenChapter: (Int) -> Unit, onExit: () -> Unit) {
    val app = LocalContext.current.app
    val library = app.stories
    val story = remember(storyId) { library.load(storyId) }
    val info = remember(storyId) { library.info(storyId) }
    // Re-read when coming back from a chapter, so the read marks are fresh.
    var read by remember { mutableStateOf(library.readChapters(storyId)) }
    var last by remember { mutableIntStateOf(library.lastChapter(storyId)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(storyId) {
        read = library.readChapters(storyId)
        last = library.lastChapter(storyId)
        onPauseOrDispose { }
    }
    val next = story.chapters.indices.firstOrNull { it !in read && it >= last } ?: story.chapters.indices.firstOrNull { it !in read } ?: 0
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
            Column(Modifier.weight(1f)) {
                Text(info?.titleKo ?: story.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text("${story.title} · ${story.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${story.chapters.size}장 · 읽은 장 ${read.size}개", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChunkyButton(
                        if (read.isEmpty()) "처음부터 읽기" else "이어 읽기 · ${story.chapters[next].title}",
                        onClick = { onOpenChapter(next) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(story.chapters, key = { i, _ -> i }) { i, chapter ->
                val done = i in read
                Row(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (done) Game.GoodSoft else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onOpenChapter(i) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Game.LagoonEdge, modifier = Modifier.widthIn(min = 28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title, style = MaterialTheme.typography.titleSmall)
                        Text("${chapter.words}단어 · 약 ${maxOf(1, (chapter.words + 149) / 150)}분", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (done) Text("읽음", style = MaterialTheme.typography.labelLarge, color = Game.OnGoodSoft)
                }
            }
        }
    }
}

/**
 * Reading one chapter. Tap a word to see its meaning in that sentence and add it to the word book;
 * long-press a paragraph to translate it. On a wide screen the word card sits beside the text;
 * on a phone or a portrait tablet it opens as a bottom sheet. The end of a chapter leads to the next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderScreen(storyId: String, chapter: Int, onExit: () -> Unit, onOpenChapter: (Int) -> Unit) {
    val app = LocalContext.current.app
    val library = app.stories
    val fullStory = remember(storyId) { library.load(storyId) }
    val info = remember(storyId) { library.info(storyId) }
    val chapterIndex = chapter.coerceIn(0, fullStory.chapters.lastIndex)
    val current = fullStory.chapters[chapterIndex]
    // The rest of this screen reads `blocks`: the blocks of this chapter only.
    val story = fullStory
    val blocks = current.blocks
    val source = StoryInfo.sourceOf(storyId)
    val sources = remember(storyId) { StoryInfo.sourcesOf(storyId) }
    var textSize by rememberSaveable { mutableIntStateOf(library.textSize) }
    var pick by rememberSaveable(chapterIndex, stateSaver = PickSaver) { mutableStateOf<Pick?>(null) }
    var showSaved by rememberSaveable { mutableStateOf(false) }
    val translations = remember(chapterIndex) { mutableStateMapOf<Int, String>() }
    val saved by remember(storyId) { app.words.bySource(*sources.toTypedArray()) }.collectAsState(initial = emptyList())
    // Highlight both the saved card's word (often the dictionary form) and the form tapped in the text.
    val savedForms = remember(saved) { savedFormsOf(saved) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = library.position(storyId, chapterIndex).coerceIn(0, blocks.lastIndex.coerceAtLeast(0)))

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
            .collect { (first, last) ->
                library.savePosition(storyId, chapterIndex, first)
                // The end item (after the last paragraph) is on screen: this chapter is read.
                if (last >= blocks.size) library.markRead(storyId, chapterIndex)
            }
    }

    // Landscape tablet: the word card sits beside the text. Phone or portrait: a bottom sheet.
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 840 && configuration.screenWidthDp > configuration.screenHeightDp
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = info?.titleKo ?: story.title,
                subtitle = "${chapterIndex + 1}/${fullStory.chapters.size} · ${current.title}",
                savedCount = saved.size,
                onBack = onExit,
                onSmaller = { textSize = (textSize - 2).coerceAtLeast(14); library.textSize = textSize },
                onBigger = { textSize = (textSize + 2).coerceAtMost(32); library.textSize = textSize },
                onSaved = { showSaved = true },
            )
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(blocks, key = { i, _ -> i }) { i, block ->
                        // A comfortable line length on wide screens.
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            if (block.isHeading) {
                                Text(block.x, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            } else {
                                TappableText(
                                    text = block.x,
                                    textSize = textSize,
                                    savedForms = savedForms,
                                    selected = pick?.takeIf { it.block == i }?.let { it.start until it.end },
                                    onTap = { range ->
                                        val word = block.x.substring(range)
                                        pick = Pick(i, range.first, range.last + 1, word, WordMaker.sentenceAround(block.x, range.first))
                                    },
                                    onLongPress = {
                                        if (translations[i] == null) {
                                            translations[i] = "번역하는 중…"
                                        }
                                    },
                                )
                                translations[i]?.let { ko ->
                                    if (ko == "번역하는 중…") {
                                        LaunchedEffect(i) {
                                            translations[i] = withTimeoutOrNull(30_000) { app.translator.translate(block.x) } ?: "번역하지 못했어요. 번역 모델이 아직 없으면 인터넷에 연결해 주세요."
                                        }
                                    }
                                    Text(
                                        ko,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .clickable { translations.remove(i) }
                                            .padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Column(
                            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (chapterIndex < fullStory.chapters.lastIndex) {
                                Text("${chapterIndex + 1}장 끝", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChunkyButton("다음 장 · ${fullStory.chapters[chapterIndex + 1].title}", onClick = { onOpenChapter(chapterIndex + 1) }, modifier = Modifier.fillMaxWidth())
                            } else {
                                Text("— 끝 —", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = onExit) { Text("장 목록으로") }
                        }
                    }
                }
                if (wide) {
                    Column(
                        Modifier
                            .width(400.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val current = pick
                        if (current == null) {
                            Text("모르는 단어를 눌러 보세요", style = MaterialTheme.typography.titleMedium)
                            Text("이 문장에서의 뜻과 예문이 여기에 나와요. 문단을 길게 누르면 번역돼요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SavedList(saved.map { it.word to it.meaning })
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("“${current.word}”", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pick = null }) { Icon(Icons.Rounded.Close, contentDescription = "닫기") }
                            }
                            WordAddCard(StoryText.lookupForm(current.word), current.sentence, source)
                        }
                    }
                }
            }
        }
    }

    val sheetPick = pick
    run {
        if (!wide && sheetPick != null) {
            ModalBottomSheet(onDismissRequest = { pick = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WordAddCard(StoryText.lookupForm(sheetPick.word), sheetPick.sentence, source)
                    TextButton(onClick = { pick = null }, modifier = Modifier.align(Alignment.End)) { Text("닫고 계속 읽기") }
                }
            }
        }
    }
    if (showSaved) {
        ModalBottomSheet(onDismissRequest = { showSaved = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
                Text("이 이야기에서 넣은 단어", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                SavedList(saved.map { it.word to it.meaning })
            }
        }
    }
}

@Composable
private fun SavedList(words: List<Pair<String, String>>) {
    if (words.isEmpty()) {
        Text("아직 넣은 단어가 없어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        words.forEach { (word, meaning) ->
            Row(
                Modifier.fillMaxWidth().background(SavedHighlight.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(word, style = MaterialTheme.typography.titleSmall, color = Color(0xFF5A4300))
                Text(meaning, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D2E00))
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    savedCount: Int,
    onBack: () -> Unit,
    onSmaller: () -> Unit,
    onBigger: () -> Unit,
    onSaved: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SavedHighlight)
                .clickable(role = Role.Button, onClick = onSaved)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("넣은 단어 $savedCount", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5A4300)) }
        IconButton(onClick = onSmaller) { Icon(Icons.Rounded.TextDecrease, contentDescription = "글자 작게") }
        IconButton(onClick = onBigger) { Icon(Icons.Rounded.TextIncrease, contentDescription = "글자 크게") }
    }
}

private val wordPattern = Regex("[A-Za-z][A-Za-z’'-]*")

@Composable
internal fun TappableText(
    text: String,
    textSize: Int,
    savedForms: Set<String>,
    selected: IntRange?,
    onTap: (IntRange) -> Unit,
    onLongPress: () -> Unit,
    fontFamily: FontFamily = FontFamily.Serif,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(text, savedForms, selected) {
        buildAnnotatedString {
            append(text)
            if (savedForms.isNotEmpty()) {
                wordPattern.findAll(text).forEach { m ->
                    if (WordDuplicates.normalize(StoryText.lookupForm(m.value)) in savedForms) {
                        addStyle(SpanStyle(background = SavedHighlight), m.range.first, m.range.last + 1)
                    }
                }
            }
            if (selected != null && selected.last < text.length) {
                addStyle(SpanStyle(background = Game.Lagoon, color = Color.White), selected.first, selected.last + 1)
            }
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * 1.6f).sp, fontFamily = fontFamily),
        color = MaterialTheme.colorScheme.onBackground,
        onTextLayout = { layout = it },
        modifier = modifier.fillMaxWidth().pointerInput(text) {
            detectTapGestures(
                onTap = { pos -> layout?.let { l -> StoryText.wordAt(text, l.getOffsetForPosition(pos))?.let(onTap) } },
                onLongPress = { onLongPress() },
            )
        },
    )
}
