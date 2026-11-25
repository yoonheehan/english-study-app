package com.grayman.englishstudy.ui.video

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.video.VideoItem
import com.grayman.englishstudy.data.video.YouTubeSearch
import com.grayman.englishstudy.ui.Game
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 10
private const val RECENT = -1

/**
 * The video section of the word book tab, like a YouTube search page: pick a topic, then a video
 * from its most viewed results (only videos with subtitles), ten per page with thumbnails.
 */
@Composable
fun VideoShelf(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val recent = remember { app.videos.recent().map { VideoItem(it.videoId, it.title, it.author, "", "") } }
    var topic by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val query = YouTubeSearch.TOPICS.getOrNull(topic)?.query
    var items by remember(topic) { mutableStateOf(if (query == null) recent else app.videoSearch.cached(query)?.items.orEmpty()) }
    var hasMore by remember(topic) { mutableStateOf(query?.let { app.videoSearch.cached(it)?.next != null } ?: false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()

    LaunchedEffect(topic, page, attempt) {
        error = null
        val q = query ?: run { items = recent; hasMore = false; return@LaunchedEffect }
        if (items.size >= (page + 1) * PAGE_SIZE) return@LaunchedEffect
        loading = true
        try {
            val result = app.videoSearch.atLeast(q, (page + 1) * PAGE_SIZE)
            items = result.items
            hasMore = result.next != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "영상 목록을 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(topic, page) { grid.scrollToItem(0) }

    val pages = (items.size + PAGE_SIZE - 1) / PAGE_SIZE
    val shown = items.drop(page * PAGE_SIZE).take(PAGE_SIZE)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        state = grid,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "주제를 고르면 조회수가 많은 영어 자막 영상이 나와요. 영상을 누르면 자막과 함께 보고, 자막의 단어를 눌러 단어장에 넣을 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recent.isNotEmpty()) TopicChip("최근 본 영상", topic == RECENT) { topic = RECENT; page = 0 }
                    YouTubeSearch.TOPICS.forEachIndexed { i, t -> TopicChip(t.titleKo, topic == i) { topic = i; page = 0 } }
                }
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = Game.Lagoon)
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error!!, style = MaterialTheme.typography.bodyLarge)
                            Text("다시 시도", color = Game.LagoonEdge, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { attempt++ }.padding(8.dp))
                        }
                        else -> Text("아직 본 영상이 없어요.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        items(shown, key = { it.videoId }) { v -> VideoCard(v) { onOpen(v.videoId) } }
        if (query != null && (pages > 1 || hasMore)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Pager(page, pages, hasMore, loading) { page = it }
            }
        }
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            NetImage(video.thumbnail, Modifier.fillMaxSize())
            if (video.length.isNotEmpty()) {
                Text(
                    video.length,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = listOf(video.channel, video.views).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Page numbers like a search page; "다음" loads more results when the loaded ones run out. */
@Composable
private fun Pager(page: Int, pages: Int, hasMore: Boolean, loading: Boolean, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "이전 페이지") }
        val first = (page - 2).coerceAtLeast(0)
        val last = (first + 4).coerceAtMost(pages - 1)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (first..last).forEach { i ->
                val on = i == page
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Game.Lagoon else Color.Transparent)
                        .clickable(role = Role.Button) { onPage(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(20.dp).padding(start = 4.dp), strokeWidth = 2.dp, color = Game.Lagoon)
        IconButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1 || hasMore) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "다음 페이지") }
    }
}

private object Thumbnails {
    val cache = LruCache<String, ImageBitmap>(120)
}

/** A small image from the network, cached while the app runs. */
@Composable
fun NetImage(url: String, modifier: Modifier = Modifier) {
    val image by produceState(Thumbnails.cache.get(url), url) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { Thumbnails.cache.put(url, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
