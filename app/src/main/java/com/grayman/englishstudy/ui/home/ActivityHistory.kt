package com.grayman.englishstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.ui.Chip
import com.grayman.englishstudy.ui.Game
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The latest lessons, listening tests, role-plays and word tests, from every synced device. */
@Composable
fun ActivityHistory() {
    val app = LocalContext.current.app
    val items by remember { app.progress.recentActivity(30) }.collectAsState(initial = emptyList())
    val format = remember { SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("최근 활동", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (items.isEmpty()) {
            Text(
                "아직 기록이 없어요. 레슨, 듣기, 말하기, 단어 외우기를 끝내면 여기에 쌓이고 서버에도 저장돼요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            val kind = ActivityKind.entries.firstOrNull { it.name == item.kind }
            val (bg, fg) = colors(kind)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip(kind?.labelKo ?: "기타", bg, fg, Modifier.width(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${item.xp} XP", style = MaterialTheme.typography.labelLarge, color = Game.Xp)
                    Text(format.format(Date(item.atMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun colors(kind: ActivityKind?): Pair<Color, Color> = when (kind) {
    ActivityKind.LESSON, ActivityKind.BONUS -> Game.LagoonSoft to Game.LagoonEdge
    ActivityKind.LISTENING -> Color(0xFFDDE7FD) to Game.Xp
    ActivityKind.ROLEPLAY -> Color(0xFFFFE4DB) to Game.CoralEdge
    ActivityKind.WORDS -> Game.GoodSoft to Game.OnGoodSoft
    ActivityKind.SPEAKING -> Color(0xFFDDE7FD) to Game.Xp
    null -> Color(0xFFEEF2F2) to Color(0xFF55636E)
}
