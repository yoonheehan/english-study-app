package com.grayman.englishstudy.ui.words

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.data.words.WordMaker
import com.grayman.englishstudy.data.words.WordRepository
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One looked-up word: its meaning in the sentence, and a button that saves it to the word book.
 * The translator answers first; the on-device AI (when downloaded) then fills the dictionary form,
 * part of speech, pronunciation and a second example. Saving puts the word at the front of the
 * new-word queue. A word already in the book is shown instead of being added twice.
 */
@Composable
fun WordAddCard(word: String, sentence: String, sourceUrl: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val tutor by app.tutor.state.collectAsState()
    val addBlocked by app.wordSync.addBlocked.collectAsState()
    var existing by remember(word) { mutableStateOf<List<WordEntity>?>(null) }
    var quickMeaning by remember(word) { mutableStateOf<String?>(null) }
    var quickSentenceKo by remember(word, sentence) { mutableStateOf("") }
    var ai by remember(word, sentence) { mutableStateOf<WordMaker.AiFill?>(null) }
    var aiRunning by remember(word, sentence) { mutableStateOf(false) }
    var edited by rememberSaveable(word) { mutableStateOf<String?>(null) }
    var result by remember(word) { mutableStateOf<WordRepository.AddResult?>(null) }
    var saving by remember(word) { mutableStateOf(false) }

    LaunchedEffect(word) {
        existing = app.words.find(word)
        quickMeaning = withTimeoutOrNull(TRANSLATE_WAIT_MS) { app.translator.translate(word) }.orEmpty()
    }
    LaunchedEffect(word, sentence) {
        if (sentence.isNotBlank()) quickSentenceKo = withTimeoutOrNull(TRANSLATE_WAIT_MS) { app.translator.translate(sentence) }.orEmpty()
    }
    // The AI runs on its own only for words that are not in the book yet.
    LaunchedEffect(word, sentence, existing, tutor.downloaded) {
        if (existing?.isEmpty() != true || !tutor.downloaded || ai != null) return@LaunchedEffect
        aiRunning = true
        try {
            var last = ""
            app.tutor.ask(WordMaker.aiPrompt(word, sentence)).collect { last = it }
            ai = WordMaker.parse(last)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            aiRunning = false
        }
    }

    val draft = WordMaker.fields(word, sentence, quickMeaning.orEmpty(), quickSentenceKo, ai, edited)
    // The meaning box follows the translator and the AI until the learner types in it.
    // TextFieldState keeps the cursor where the learner taps (a String value fought the keyboard).
    val meaningField = rememberTextFieldState()
    var lastSetByApp by remember(word) { mutableStateOf("") }
    LaunchedEffect(draft.meaning, edited == null) {
        if (edited == null && meaningField.text.toString() != draft.meaning) {
            lastSetByApp = draft.meaning
            meaningField.setTextAndPlaceCursorAtEnd(draft.meaning)
        }
    }
    LaunchedEffect(meaningField) {
        snapshotFlow { meaningField.text.toString() }.collect { text -> if (text != lastSetByApp) edited = text }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(draft.word, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                val sub = listOf(partOfSpeechKo(draft.partOfSpeech), draft.pronunciation).filter { it.isNotEmpty() }.joinToString("  ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { scope.launch { app.voice.speak(draft.word, app.voice.defaultAccent()) } }) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "발음 듣기", tint = Game.Lagoon)
            }
        }
        val known = existing
        when {
            known == null -> Text("단어장을 확인하는 중…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            known.isNotEmpty() && result == null -> {
                known.take(2).forEach { w ->
                    Text(
                        listOf(partOfSpeechKo(w.partOfSpeech), w.meaning).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    if (known.any { it.mastery >= 100 }) "이미 단어장에 있고, 외운 단어예요." else "이미 단어장에 있어요. 아래 버튼을 누르면 다음 단어 외우기에서 먼저 나와요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Game.LagoonEdge,
                )
                if (known.none { it.mastery >= 100 || it.priority }) {
                    ChunkyButton("먼저 외우기로 표시", onClick = {
                        scope.launch { result = app.words.add(draft.copy(partOfSpeech = known.first().partOfSpeech), sentence, sourceUrl) }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            result != null -> {
                Text(
                    when (val r = result) {
                        is WordRepository.AddResult.Added -> "단어장에 넣었어요. 다음 '새 단어' 외우기에서 먼저 나와요." +
                            (if (addBlocked) "\n(아직 이 기기에만 있어요. Firestore 규칙을 새로 게시하면 다른 기기로도 올라가요.)" else "")
                        is WordRepository.AddResult.AlreadyThere -> "이미 있는 단어라 새로 넣지 않고, 먼저 외우기로 표시했어요."
                        null -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Game.OnGoodSoft,
                    modifier = Modifier.fillMaxWidth().background(Game.GoodSoft, RoundedCornerShape(12.dp)).padding(12.dp),
                )
            }
            else -> {
                OutlinedTextField(
                    state = meaningField,
                    label = { Text("뜻 (고쳐서 넣을 수 있어요)") },
                    placeholder = { Text(if (quickMeaning == null) "뜻 찾는 중…" else "뜻을 적어 주세요") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (quickMeaning == null && edited == null) {
                    Text("뜻 찾는 중…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (aiRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Game.Lagoon)
                        Text("AI가 이 문장에 맞는 뜻과 예문을 만드는 중… 기다리지 않고 넣어도 돼요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (ai != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Game.Lagoon, modifier = Modifier.size(16.dp))
                        Text("AI가 채운 내용이에요", style = MaterialTheme.typography.labelMedium, color = Game.LagoonEdge)
                    }
                }
                if (draft.example1.isNotBlank()) {
                    Text(draft.example1, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (draft.example1Meaning.isNotBlank()) Text(draft.example1Meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (draft.example2.isNotBlank()) {
                    Text(draft.example2, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (draft.example2Meaning.isNotBlank()) Text(draft.example2Meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChunkyButton(
                    if (saving) "넣는 중…" else "단어장에 넣기",
                    icon = Icons.Rounded.Add,
                    enabled = !saving && draft.meaning.isNotBlank(),
                    onClick = {
                        saving = true
                        scope.launch {
                            result = app.words.add(draft, sentence, sourceUrl)
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val TRANSLATE_WAIT_MS = 20_000L
