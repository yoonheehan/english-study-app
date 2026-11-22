package com.grayman.englishstudy.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.domain.StudySettings
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.words.partOfSpeechKo
import kotlinx.coroutines.launch

/** What the word list on the left shows; hiding one side turns the list into a quick self-check. */
private enum class Cover(val labelKo: String) { NONE("모두 보기"), MEANING("뜻 가리기"), WORD("영어 가리기") }

/**
 * Before the test: the picked words, to memorize at the learner's own pace. The list sits on the
 * left and the selected word's card on the right; every word must be opened once before the
 * test button lights up, unless the learner chooses to start anyway.
 */
@Composable
fun MemorizeStep(settings: StudySettings, words: List<WordEntity>, onExit: () -> Unit, onStart: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val accents by app.voice.accents.collectAsState()
    var selected by remember { mutableIntStateOf(0) }
    var cover by remember { mutableStateOf(Cover.NONE) }
    val seen = remember { mutableStateOf(setOf(words.first().id)) }
    val listState = rememberLazyListState()
    fun speak(text: String) = scope.launch { app.voice.speak(text, app.voice.defaultAccent()) }
    fun select(i: Int) {
        selected = i.coerceIn(0, words.lastIndex)
        seen.value = seen.value + words[selected].id
    }
    LaunchedEffect(selected) { listState.animateScrollToItem(selected) }
    // The picked words are drawn at random; leaving by mistake would lose this set, so ask first.
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    if (confirmExit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("외우기를 그만둘까요?") },
            text = { Text("지금 나가면 이번에 고른 ${words.size}개 단어 목록이 사라지고, 다음엔 새로 골라요.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { Text("계속 외우기") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onExit) { Text("그만두기") } },
        )
    }

    val allSeen = seen.value.size == words.size
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Tablet: list beside the card, buttons in one row. Phone: card above the list, buttons stacked.
        val wide = maxWidth >= 720.dp
        val coverChips: @Composable () -> Unit = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cover.entries.forEach { option ->
                    val on = cover == option
                    Text(
                        option.labelKo,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.RadioButton) { cover = option }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { confirmExit = true }) { Icon(Icons.Rounded.Close, contentDescription = "그만두기") }
                Column(Modifier.weight(1f)) {
                    Text("${settings.source.labelKo} ${words.size}개 외우기", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "충분히 외운 다음 시험을 봐요. 본 단어 ${seen.value.size} / ${words.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (wide) coverChips()
            }
            if (!wide) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { coverChips() }

            val list: @Composable (Modifier) -> Unit = { m ->
                LazyColumn(
                    m.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface),
                    state = listState,
                ) {
                    itemsIndexed(words, key = { _, w -> w.id }) { i, word ->
                        WordLine(i, word, cover, selected = i == selected, seen = word.id in seen.value) { select(i) }
                    }
                }
            }
            val card: @Composable (Modifier) -> Unit = { m ->
                WordCard(words[selected], selected, words.size, cover, compact = !wide, m, onSpeak = ::speak, onPrev = { select(selected - 1) }, onNext = { select(selected + 1) })
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    list(Modifier.weight(0.38f).fillMaxHeight())
                    card(Modifier.weight(0.62f).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    card(Modifier.fillMaxWidth().weight(0.62f))
                    list(Modifier.fillMaxWidth().weight(0.38f))
                }
            }

            val hint = if (allSeen) "다 봤어요. 준비되면 시험을 시작하세요." else "단어를 하나씩 눌러 보거나 화살표로 넘기세요."
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (!allSeen) SecondaryButton("바로 시험 보기", onStart, Modifier.width(180.dp))
                    ChunkyButton("다 외웠어요 · 시험 보기", onClick = onStart, enabled = allSeen, icon = Icons.Rounded.Edit, modifier = Modifier.width(320.dp))
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!allSeen) SecondaryButton("바로 시험", onStart, Modifier.weight(0.4f))
                        ChunkyButton("시험 보기", onClick = onStart, enabled = allSeen, icon = Icons.Rounded.Edit, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordLine(index: Int, word: WordEntity, cover: Cover, selected: Boolean, seen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Game.LagoonSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (cover == Cover.WORD) "· · ·" else word.word,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (cover == Cover.MEANING) "· · ·" else word.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (seen) Icon(Icons.Rounded.CheckCircle, contentDescription = "봤어요", tint = Game.Good, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WordCard(
    word: WordEntity,
    index: Int,
    total: Int,
    cover: Cover,
    compact: Boolean,
    modifier: Modifier,
    onSpeak: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    // A covered side stays hidden on the card too, until tapped; each word starts covered again.
    var revealed by remember(word.id, cover) { mutableStateOf(false) }
    val hideWord = cover == Cover.WORD && !revealed
    val hideMeaning = cover == Cover.MEANING && !revealed
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(if (compact) 16.dp else 28.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (word.partOfSpeech.isNotEmpty()) {
                Text(partOfSpeechKo(word.partOfSpeech), style = MaterialTheme.typography.labelLarge, color = Game.LagoonEdge)
            }
            if (hideWord) {
                Hidden("영어 단어 · 눌러서 보기") { revealed = true }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, fontSize = if (compact) 32.sp else 44.sp, lineHeight = if (compact) 38.sp else 52.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                IconButton(onClick = { onSpeak(word.word) }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "${word.word} 발음 듣기", tint = Game.Lagoon)
                }
            }
            if (word.pronunciation.isNotEmpty() && !hideWord) {
                Text(word.pronunciation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hideMeaning) {
                Hidden("뜻 · 눌러서 보기") { revealed = true }
                return@Column
            }
            Text(word.meaning, fontSize = if (compact) 22.sp else 28.sp, lineHeight = if (compact) 28.sp else 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (hideWord) return@Column
            listOf(word.example1 to word.example1Meaning, word.example2 to word.example2Meaning, word.sourceSentence to "")
                .filter { it.first.isNotEmpty() }
                .distinctBy { it.first }
                .forEach { (sentence, meaning) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSpeak(sentence) }
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Game.Lagoon, modifier = Modifier.size(18.dp))
                            Text(sentence, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (meaning.isNotEmpty()) Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            if (word.memo.isNotEmpty()) {
                Text("메모: ${word.memo}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = index > 0) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "이전 단어") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("${index + 1} / $total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, enabled = index < total - 1) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "다음 단어") }
        }
    }
}

@Composable
private fun Hidden(label: String, onReveal: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Game.LagoonSoft)
            .clickable(role = Role.Button, onClick = onReveal)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Game.LagoonEdge)
    }
}
