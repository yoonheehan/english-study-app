package com.grayman.englishstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ActivityKind
import com.grayman.englishstudy.data.DailyRecordEntity
import com.grayman.englishstudy.ui.Game
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The full study calendar, opened from the week strip: year and month at the top, days studied
 * marked with their XP, and the chosen day's activities beside the month (below it on a phone).
 */
@Composable
fun StudyCalendarDialog(records: List<DailyRecordEntity>, today: Long, onDismiss: () -> Unit) {
    val byDay = remember(records) { records.associateBy { it.epochDay } }
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(today))) }
    var selected by remember { mutableLongStateOf(today) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("학습 달력", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                val monthDays = byDay.values.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month && it.xp > 0 }
                Text(
                    "이번 달 ${monthDays.size}일 공부 · ${monthDays.sumOf { it.xp }} XP",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "닫기") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Side by side only when the window is wide and landscape; in portrait the day's
                // details go below the month so the calendar keeps its full width.
                val wide = maxWidth >= 720.dp && maxWidth > maxHeight
                val calendar: @Composable (Modifier) -> Unit = { m ->
                    MonthGrid(month, byDay, today, selected, m, onMonth = { month = it }, onSelect = { selected = it })
                }
                val detail: @Composable (Modifier) -> Unit = { m -> DayDetail(selected, byDay[selected], m) }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        calendar(Modifier.weight(0.6f).fillMaxHeight())
                        detail(Modifier.weight(0.4f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        calendar(Modifier.fillMaxWidth())
                        detail(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    byDay: Map<Long, DailyRecordEntity>,
    today: Long,
    selected: Long,
    modifier: Modifier,
    onMonth: (YearMonth) -> Unit,
    onSelect: (Long) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "이전 달") }
            Text(
                "${month.year}년 ${month.monthValue}월",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "다음 달") }
        }
        Row {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = (List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }).chunked(7)
        cells.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date -> DayCell(date, byDay, today, selected, Modifier.weight(1f), onSelect) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            "주황색 칸은 공부한 날이에요. 날짜를 누르면 그날 한 것을 볼 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    byDay: Map<Long, DailyRecordEntity>,
    today: Long,
    selected: Long,
    modifier: Modifier,
    onSelect: (Long) -> Unit,
) {
    if (date == null) {
        Spacer(modifier.height(CELL_HEIGHT))
        return
    }
    val day = date.toEpochDay()
    val xp = byDay[day]?.xp ?: 0
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .height(CELL_HEIGHT)
            .background(if (xp > 0) STUDIED_BG else MaterialTheme.colorScheme.surface, shape)
            .border(
                width = if (day == selected) 3.dp else if (day == today || xp > 0) 2.dp else 1.dp,
                color = when {
                    day == selected -> Game.LagoonEdge
                    day == today -> Game.Lagoon
                    xp > 0 -> Game.Flame
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = shape,
            )
            .clickable(role = Role.Button) { onSelect(day) }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            color = if (xp > 0) STUDIED_TEXT else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (xp > 0) {
            Text(
                "$xp XP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = STUDIED_TEXT,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun DayDetail(day: Long, record: DailyRecordEntity?, modifier: Modifier) {
    val app = LocalContext.current.app
    val items by remember(day) { app.progress.activityOn(day) }.collectAsState(initial = emptyList())
    val date = LocalDate.ofEpochDay(day)
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 (${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})",
            style = MaterialTheme.typography.titleMedium,
        )
        if (record == null || record.xp == 0) {
            Text("이날은 공부 기록이 없어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "${record.xp} XP · 레슨 ${record.lessons}개 · 말한 시간 ${record.speakingSeconds / 60}분 ${record.speakingSeconds % 60}초",
                style = MaterialTheme.typography.bodyMedium,
                color = Game.LagoonEdge,
            )
        }
        items.forEach { item ->
            val kind = ActivityKind.entries.firstOrNull { it.name == item.kind }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.width(64.dp).background(Game.LagoonSoft, RoundedCornerShape(8.dp)).padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(kind?.labelKo ?: "기타", style = MaterialTheme.typography.labelMedium, color = Game.LagoonEdge) }
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("+${item.xp}", style = MaterialTheme.typography.labelLarge, color = Game.Xp)
            }
        }
        if (items.isEmpty() && record != null && record.xp > 0) {
            Text(
                "자세한 활동 기록은 이번 업데이트 이후부터 남아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CELL_HEIGHT = 64.dp
private val STUDIED_BG = Color(0xFFFFE3BF)
private val STUDIED_TEXT = Color(0xFF8A3B00)

/** Accent choice: one fixed accent, or "섞기" to rotate through every installed accent. */
@Composable
fun AccentPicker(modifier: Modifier = Modifier) {
    val voice = LocalContext.current.app.voice
    val accents by voice.accents.collectAsState()
    val preferred by voice.preferred.collectAsState()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val options = listOf<Pair<String, com.grayman.englishstudy.speech.Accent?>>("섞기" to null) + accents.map { it.labelKo to it }
        options.forEach { (label, accent) ->
            val on = preferred == accent || (accent == null && preferred !in accents)
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Game.Lagoon else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { voice.setPreferred(accent) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
