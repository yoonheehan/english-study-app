package com.grayman.englishstudy.ui.video

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.StoryText
import com.grayman.englishstudy.data.video.RecentVideo
import com.grayman.englishstudy.data.video.VideoCaptions
import com.grayman.englishstudy.data.video.YouTubeCaptions
import com.grayman.englishstudy.data.video.YouTubeLinks
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SecondaryButton
import com.grayman.englishstudy.ui.reader.SavedHighlight
import com.grayman.englishstudy.ui.reader.TappableText
import com.grayman.englishstudy.ui.reader.savedFormsOf
import com.grayman.englishstudy.ui.words.WordAddCard
import kotlinx.coroutines.withTimeoutOrNull

/** The video section of the word book tab: paste a YouTube link, or reopen a recent video. */
@Composable
fun VideoShelf(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    var link by rememberSaveable { mutableStateOf("") }
    val recent = remember { app.videos.recent() }
    val id = YouTubeLinks.videoId(link)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Game.LagoonSoft, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("영상 보며 단어 모으기", style = MaterialTheme.typography.titleLarge, color = Game.LagoonEdge, fontWeight = FontWeight.Bold)
            Text(
                "유튜브 링크를 붙여넣으면 영상과 영어 자막을 함께 보여 줘요. 자막의 단어를 누르면 뜻이 나오고 바로 단어장에 넣을 수 있어요. 유튜브 앱에서 '공유 → English Daily'로 보내도 돼요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                singleLine = true,
                label = { Text("유튜브 링크") },
                placeholder = { Text("https://youtu.be/…") },
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                "영상과 자막 열기",
                onClick = { id?.let(onOpen) },
                enabled = id != null,
                icon = Icons.Rounded.Subtitles,
                modifier = Modifier.fillMaxWidth(),
            )
            if (link.isNotBlank() && id == null) {
                Text("유튜브 링크가 아닌 것 같아요.", style = MaterialTheme.typography.bodySmall, color = Game.Bad)
            }
        }
        if (recent.isNotEmpty()) {
            Text("최근에 본 영상", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            recent.forEach { VideoRow(it) { onOpen(it.videoId) } }
        }
        Text("먼저 이걸로 해 보세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        YouTubeCaptions.SUGGESTED.filter { s -> recent.none { it.videoId == s.videoId } }.forEach { VideoRow(it) { onOpen(it.videoId) } }
        Text(
            "영어 자막(자동 자막 포함)이 있는 영상만 돼요. 자막은 유튜브 앱이 쓰는 방식으로 가져와서, 유튜브가 바꾸면 안 될 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoRow(video: RecentVideo, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(video.title.ifBlank { video.videoId }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        if (video.author.isNotBlank()) Text(video.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private sealed interface Load {
    data object Loading : Load
    data class Failed(val message: String) : Load
    data class Ready(val captions: VideoCaptions) : Load
}

private data class VideoPick(val cue: Int, val start: Int, val end: Int, val word: String, val sentence: String)

/**
 * Watching one video: the player on top (or on the left of a landscape tablet) and the English
 * subtitles below it, following the video. Tap a word to pause and see its meaning; tap a time to
 * jump there. "한 줄 반복" loops the current line, and Korean subtitles can be shown under each line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStudyScreen(videoId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var load by remember(videoId) { mutableStateOf<Load>(Load.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(videoId, attempt) {
        load = Load.Loading
        load = try {
            Load.Ready(app.videos.load(videoId))
        } catch (e: YouTubeCaptions.NoCaptions) {
            Load.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            Load.Failed("자막을 가져오지 못했어요: ${e.message}")
        }
    }
    val controller = remember(videoId) { YouTubeController() }
    val source = YouTubeLinks.watchUrl(videoId)
    val saved by remember(source) { app.words.bySource(source) }.collectAsState(initial = emptyList())
    val savedForms = remember(saved) { savedFormsOf(saved) }
    var pick by remember { mutableStateOf<VideoPick?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var showKorean by rememberSaveable { mutableStateOf(false) }
    var loop by rememberSaveable { mutableStateOf(false) }
    var rate by rememberSaveable { mutableFloatStateOf(1f) }
    val translated = remember(videoId) { mutableStateMapOf<Int, String>() }
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 840 && configuration.screenWidthDp > configuration.screenHeightDp

    val captions = (load as? Load.Ready)?.captions
    val current = captions?.indexAt(controller.timeMs) ?: -1
    // Keep the playing line in view, unless the learner is looking a word up.
    LaunchedEffect(current, follow) {
        if (follow && current >= 0 && pick == null) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    // "한 줄 반복": jump back to the start of the line when it ends.
    var loopLine by remember { mutableIntStateOf(-1) }
    LaunchedEffect(loop) { loopLine = if (loop) current.coerceAtLeast(0) else -1 }
    LaunchedEffect(controller.timeMs) {
        val c = captions ?: return@LaunchedEffect
        val line = loopLine
        if (loop && line in c.cues.indices && controller.timeMs >= c.cues[line].endMs) controller.seekTo(c.cues[line].startMs)
    }

    fun jump(index: Int) {
        val c = captions ?: return
        val i = index.coerceIn(0, c.cues.lastIndex)
        if (loop) loopLine = i
        controller.seekTo(c.cues[i].startMs)
    }

    val player: @Composable (Modifier) -> Unit = { m ->
        Box(m.aspectRatio(16f / 9f).background(Color.Black)) {
            YouTubePlayer(videoId, controller, Modifier.fillMaxSize())
            controller.error?.let { code ->
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (code == 101 || code == 150) "이 영상은 주인이 다른 앱에서 재생하지 못하게 막아 두었어요. 자막은 아래에서 그대로 볼 수 있어요."
                        else "영상을 재생하지 못했어요 (오류 $code). 자막은 아래에서 볼 수 있어요.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("유튜브에서 열기", color = Color.White) }
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { jump(current - 1) }, enabled = captions != null) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "이전 줄") }
            IconButton(onClick = controller::toggle, enabled = controller.ready) {
                Icon(if (controller.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (controller.playing) "멈춤" else "재생", tint = Game.Lagoon, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { jump(current + 1) }, enabled = captions != null) { Icon(Icons.Rounded.SkipNext, contentDescription = "다음 줄") }
            Chip(if (loop) "한 줄 반복 켜짐" else "한 줄 반복", loop, Icons.Rounded.RepeatOne) { loop = !loop }
            Chip("${if (rate == 1f) "1" else rate}배속", rate != 1f) {
                rate = when (rate) { 1f -> 0.75f; 0.75f -> 0.5f; else -> 1f }
                controller.setRate(rate)
            }
            Chip("자막 따라가기", follow) { follow = !follow }
            Chip("한글 자막", showKorean) { showKorean = !showKorean }
        }
    }
    val subtitleList: @Composable (Modifier) -> Unit = { m ->
        when (val l = load) {
            Load.Loading -> Box(m, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Game.Lagoon)
                    Text("자막을 가져오는 중…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is Load.Failed -> Column(m.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(l.message, style = MaterialTheme.typography.bodyLarge)
                SecondaryButton("다시 시도", { attempt++ }, Modifier.width(160.dp))
            }
            is Load.Ready -> LazyColumn(
                m,
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(l.captions.cues, key = { i, _ -> i }) { i, cue ->
                    val playing = i == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (playing) Game.LagoonSoft else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            timeLabel(cue.startMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (playing) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { jump(i) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .widthIn(min = 40.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            TappableText(
                                text = cue.text,
                                textSize = 18,
                                savedForms = savedForms,
                                selected = pick?.takeIf { it.cue == i }?.let { it.start until it.end },
                                onTap = { range ->
                                    controller.pause()
                                    val cues = l.captions.cues
                                    // Automatic captions are short fragments; the next one gives the word more context.
                                    val sentence = if (l.captions.autoGenerated && i + 1 < cues.size) "${cue.text} ${cues[i + 1].text}" else cue.text
                                    pick = VideoPick(i, range.first, range.last + 1, cue.text.substring(range), sentence)
                                },
                                onLongPress = { jump(i) },
                                fontFamily = FontFamily.Default,
                            )
                            if (showKorean) {
                                val own = l.captions.korean.getOrNull(i)?.takeIf { it.isNotBlank() }
                                if (own == null && l.captions.korean.isEmpty() && translated[i] == null) {
                                    LaunchedEffect(i) { translated[i] = withTimeoutOrNull(20_000) { app.translator.translate(cue.text) }.orEmpty() }
                                }
                                (own ?: translated[i])?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
            Column(Modifier.weight(1f)) {
                Text(captions?.title ?: "영상", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val sub = listOfNotNull(captions?.author, captions?.let { if (it.autoGenerated) "자동 자막" else "영어 자막" }, captions?.takeIf { it.korean.isNotEmpty() }?.let { "한글 자막 있음" })
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SavedHighlight).padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("넣은 단어 ${saved.size}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF5A4300)) }
            Box(Modifier.width(8.dp))
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).imePadding()) {
                    player(Modifier.fillMaxWidth())
                    controls()
                    pick?.let { p ->
                        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("“${p.word}”", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pick = null }) { Icon(Icons.Rounded.Close, contentDescription = "닫기") }
                            }
                            WordAddCard(StoryText.lookupForm(p.word), p.sentence, source)
                        }
                    } ?: Text(
                        "자막의 모르는 단어를 누르면 영상이 멈추고 뜻이 여기에 나와요. 시간을 누르면 그 줄로 가요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                subtitleList(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            player(Modifier.fillMaxWidth())
            controls()
            subtitleList(Modifier.fillMaxWidth().weight(1f))
        }
    }

    val sheetPick = pick
    if (!wide && sheetPick != null) {
        ModalBottomSheet(onDismissRequest = { pick = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WordAddCard(StoryText.lookupForm(sheetPick.word), sheetPick.sentence, source)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { pick = null }) { Text("닫기") }
                    TextButton(onClick = {
                        pick = null
                        controller.play()
                    }) { Text("닫고 이어 보기") }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Game.LagoonSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = if (on) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Game.LagoonEdge else MaterialTheme.colorScheme.onSurface)
    }
}

private fun timeLabel(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
