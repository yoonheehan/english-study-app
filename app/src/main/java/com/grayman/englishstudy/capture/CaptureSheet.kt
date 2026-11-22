package com.grayman.englishstudy.capture

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.KoreanTranslator
import com.grayman.englishstudy.data.words.WordEntity
import com.grayman.englishstudy.data.words.WordFields
import com.grayman.englishstudy.domain.CaptureText
import com.grayman.englishstudy.speech.Accent
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.data.words.WordMaker
import com.grayman.englishstudy.ui.words.WordAddCard
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.words.partOfSpeechKo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CaptureSheet(input: CaptureInput, onClose: () -> Unit) {
    val text = input.text
    val tokens = remember(text) { CaptureText.tokens(text) }
    val short = remember(text) { CaptureText.isShortSelection(text) }
    // A short selection is the word itself, so it starts picked; a sentence waits for taps.
    var picked by remember(text) {
        mutableStateOf(if (short) tokens.indices.filter { tokens[it].isWord }.toSet() else emptySet())
    }
    val picks = remember(picked) { CaptureText.picks(text, tokens, picked) }

    // Tapping outside closes, like any bottom sheet.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                // A fixed height, so picking words never moves the sentence under the finger.
                .fillMaxHeight(0.8f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                // Swallows taps so touching the sheet does not close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("단어 찾기", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "닫기") }
            }
            when {
                text.isEmpty() || CaptureText.isLinkOnly(text) -> Hint(
                    "링크만 받았어요. 글에서 단어나 문장을 길게 눌러 선택한 뒤, 메뉴에서 '단어 찾기'를 눌러 주세요.",
                )
                tokens.none { it.isWord } -> Hint("영어 단어가 없어요. 영어 단어나 문장을 선택해 주세요.")
                else -> CaptureContent(text, tokens, short, picked, onToggle = { index ->
                    picked = if (index in picked) picked - index else picked + index
                }, picks = picks, sourceUrl = input.sourceUrl, onClose = onClose)
            }
        }
    }
}

@Composable
private fun Hint(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureContent(
    text: String,
    tokens: List<CaptureText.Token>,
    short: Boolean,
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    picks: List<CaptureText.Pick>,
    sourceUrl: String?,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val modelState by app.translator.state.collectAsState()
    var sentenceKo by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (!short) sentenceKo = withTimeoutOrNull(TRANSLATE_WAIT_MS) { app.translator.translate(text) }
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!short) {
            Text(
                "모르는 단어를 눌러 고르세요. 붙어 있는 단어를 함께 고르면 구로 찾아요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tokens.forEachIndexed { index, token ->
                    if (token.isWord) {
                        val on = index in picked
                        Text(
                            token.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) Game.Lagoon else Color.Transparent)
                                .clickable(role = Role.Checkbox) { onToggle(index) }
                                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                    } else if (token.text.isBlank()) {
                        Spacer(Modifier.width(6.dp))
                    } else {
                        // Punctuation sits right after its word, without the gap of a separate chip.
                        Text(token.text.trim(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            sentenceKo?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ModelNotice(modelState)

        picks.forEach { pick ->
            // A long selection is the sentence; a short one is the word itself, with no sentence to keep.
            val sentence = if (short) "" else WordMaker.sentenceAround(text, text.indexOf(pick.phrase).coerceAtLeast(0))
            WordAddCard(pick.phrase, sentence, sourceUrl.orEmpty(), Modifier.fillMaxWidth())
        }

        SecondaryButton("닫기", onClick = onClose, modifier = Modifier.fillMaxWidth())
        Box(Modifier.size(8.dp))
    }
}

@Composable
private fun ModelNotice(state: KoreanTranslator.ModelState) {
    val message = when (state) {
        KoreanTranslator.ModelState.DOWNLOADING -> "처음 한 번 번역 모델(약 30MB)을 받는 중이에요."
        KoreanTranslator.ModelState.FAILED -> "번역 모델을 받지 못했어요. 인터넷에 연결하면 다음에 자동으로 받아요. 지금은 뜻을 직접 적어 주세요."
        else -> null
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state == KoreanTranslator.ModelState.DOWNLOADING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Game.Lagoon)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val CLOSE_DELAY_MS = 1_200L
private const val TRANSLATE_WAIT_MS = 20_000L
