package com.grayman.englishstudy.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.Chip
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun WordDetail(id: String, onBack: (() -> Unit)?) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val word by remember(id) { app.words.observe(id) }.collectAsState(initial = null)

    val current = word
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "목록으로") }
            }
            Box(Modifier.weight(1f))
            if (current != null && !current.deleted) {
                IconButton(onClick = { scope.launch { app.words.setFavorite(id, !current.favorite) } }) {
                    Icon(
                        if (current.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (current.favorite) "즐겨찾기 해제" else "즐겨찾기",
                        tint = if (current.favorite) Game.Flame else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (current == null || current.deleted) {
            Text(
                "단어를 찾을 수 없어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(current.word, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current.partOfSpeech.isNotEmpty()) {
                    Chip(partOfSpeechKo(current.partOfSpeech), Game.LagoonSoft, Game.LagoonEdge)
                }
                if (current.pronunciation.isNotEmpty()) {
                    Text(current.pronunciation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AccentButtons(current.word)

            Card("뜻") {
                Text(current.meaning, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            // A word saved while reading keeps its sentence as example 1 too; show it once, with its translation.
            val examples = listOf(current.example1 to current.example1Meaning, current.example2 to current.example2Meaning)
                .filter { it.first.isNotEmpty() }
            if (current.sourceSentence.isNotEmpty()) {
                val meaning = examples.firstOrNull { it.first == current.sourceSentence }?.second.orEmpty()
                Card("읽다가 저장한 문장") { Example(current.sourceSentence, meaning) }
            }
            val otherExamples = examples.filter { it.first != current.sourceSentence }
            if (otherExamples.isNotEmpty()) {
                Card("예문") {
                    otherExamples.forEach { (sentence, meaning) -> Example(sentence, meaning) }
                }
            }
            if (current.memo.isNotEmpty()) {
                Card("메모") {
                    Text(current.memo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            LearningCard(current) { mastered -> scope.launch { app.words.setMastered(id, mastered) } }
        }
    }
}

/** Hearing a word in several accents is the point of this app, so every installed accent gets a button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentButtons(text: String) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val accents by app.voice.accents.collectAsState()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accents.ifEmpty { listOf(Accent.US) }.forEach { accent ->
            SecondaryButton(
                accent.labelKo,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onClick = { scope.launch { app.voice.speak(text, accent) } },
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Example(sentence: String, meaning: String) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val accents by app.voice.accents.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) {
                scope.launch { app.voice.speak(sentence, app.voice.defaultAccent()) }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "예문 듣기",
            tint = Game.Lagoon,
            modifier = Modifier.padding(top = 2.dp, end = 10.dp).size(20.dp),
        )
        Column {
            Text(sentence, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (meaning.isNotEmpty()) {
                Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LearningCard(word: WordEntity, onMastered: (Boolean) -> Unit) {
    val mastered = word.mastery >= 100
    Card("학습 상태") {
        Text(
            when {
                mastered && word.nextReviewAt > 0 -> "외운 단어 · 다음 복습 ${formatDay(word.nextReviewAt)}"
                mastered -> "외운 단어 · 오늘 복습"
                else -> "외우는 중"
            } + if (word.wrongCount > 0) " · 틀린 횟수 ${word.wrongCount}" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (mastered) {
            SecondaryButton("다시 외우기", onClick = { onMastered(false) }, modifier = Modifier.fillMaxWidth())
        } else {
            ChunkyButton("외웠어요", onClick = { onMastered(true) }, color = Game.Good, edge = Game.GoodEdge, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatDay(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일"))
