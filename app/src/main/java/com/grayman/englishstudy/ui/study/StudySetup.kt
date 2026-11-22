package com.grayman.englishstudy.ui.study

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.words.WordCounts
import com.grayman.englishstudy.domain.AnswerMethod
import com.grayman.englishstudy.domain.StudyDirection
import com.grayman.englishstudy.domain.StudySettings
import com.grayman.englishstudy.domain.StudySource
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game

/** Chooses which words to test, how many, and how to answer. Opens from the word tab. */
@Composable
fun StudySetup(onStart: (StudySettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var settings by remember { mutableStateOf(StudyPrefs.load(context)) }
    val counts by remember { app.words.counts() }.collectAsState(initial = WordCounts(0, 0, 0, 0))
    val wrong by remember { app.words.wrongCount() }.collectAsState(initial = 0)
    val priority by remember { app.words.priorityCount() }.collectAsState(initial = 0)
    val available = when (settings.source) {
        StudySource.NEW -> counts.total - counts.mastered
        StudySource.DUE -> counts.due
        StudySource.WRONG -> wrong
        StudySource.FAVORITE -> counts.favorite
    }
    fun update(change: StudySettings) {
        settings = change
        StudyPrefs.save(context, change)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
                Column {
                    Text("단어 외우기", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "고른 단어를 먼저 외우고 시험을 봐요. 다 맞힐 때까지 틀린 문제가 다시 나오고, 마치면 1·3·7·14·30일 뒤에 복습으로 나와요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val sourceAndCount: @Composable ColumnScope.() -> Unit = {
                Section("어떤 단어를 볼까요?")
                StudySource.entries.forEach { source ->
                    val n = when (source) {
                        StudySource.NEW -> counts.total - counts.mastered
                        StudySource.DUE -> counts.due
                        StudySource.WRONG -> wrong
                        StudySource.FAVORITE -> counts.favorite
                    }
                    OptionCard(
                        title = "${source.labelKo} · ${n}개",
                        description = if (source == StudySource.NEW && priority > 0) {
                            "읽다가 넣은 단어 ${priority}개가 먼저 나오고, 나머지는 무작위로 골라요."
                        } else {
                            source.descriptionKo
                        },
                        selected = settings.source == source,
                        onClick = { update(settings.copy(source = source)) },
                    )
                }
                Section("몇 개를 무작위로 고를까요?")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudySettings.COUNTS.forEach { n ->
                        CountChip(n, selected = settings.count == n, Modifier.weight(1f)) { update(settings.copy(count = n)) }
                    }
                }
            }
            val directionAndMethod: @Composable ColumnScope.() -> Unit = {
                Section("무엇을 쓸까요?")
                StudyDirection.entries.forEach { direction ->
                    OptionCard(direction.labelKo, direction.descriptionKo, settings.direction == direction) {
                        update(settings.copy(direction = direction))
                    }
                }
                Section("답은 어떻게 쓸까요?")
                AnswerMethod.entries.forEach { method ->
                    OptionCard(method.labelKo, method.descriptionKo, settings.method == method) {
                        update(settings.copy(method = method))
                    }
                }
            }

            if (wide) {
                Row(Modifier.weight(1f).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = sourceAndCount)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = directionAndMethod)
                }
            } else {
                Column(
                    Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sourceAndCount()
                    directionAndMethod()
                }
            }

            val take = minOf(settings.count, available)
            val summary = when {
                available == 0 -> "${settings.source.labelKo}가 지금은 없어요."
                take < settings.count -> "${settings.source.labelKo}가 ${available}개뿐이라 ${take}개로 시험을 봐요."
                else -> "${settings.source.labelKo} ${take}개 · ${settings.direction.labelKo} · ${settings.method.labelKo}"
            }
            val start: @Composable (Modifier) -> Unit = { modifier ->
                ChunkyButton(
                    text = if (available == 0) "볼 단어가 없어요" else "${take}개 시작",
                    onClick = { onStart(settings) },
                    enabled = available > 0,
                    icon = Icons.Rounded.Edit,
                    modifier = modifier,
                )
            }
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    start(Modifier.width(300.dp))
                }
            } else {
                // On a phone the summary sits above a full-width button, so neither is squeezed.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    start(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun OptionCard(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Game.LagoonSoft else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (selected) Game.Lagoon else MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Game.LagoonEdge else MaterialTheme.colorScheme.onSurface,
        )
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CountChip(n: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (selected) Game.Lagoon else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (selected) Game.LagoonEdge else MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${n}개",
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Remembers the last choices, so the usual test is one tap away. */
internal object StudyPrefs {
    private const val NAME = "word_study"

    fun load(context: Context): StudySettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val d = StudySettings()
        return StudySettings(
            source = enumOr(p.getString("source", null), d.source),
            count = p.getInt("count", d.count).takeIf { it in StudySettings.COUNTS } ?: d.count,
            direction = enumOr(p.getString("direction", null), d.direction),
            method = enumOr(p.getString("method", null), d.method),
        )
    }

    fun save(context: Context, s: StudySettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("source", s.source.name)
            .putInt("count", s.count)
            .putString("direction", s.direction.name)
            .putString("method", s.method.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
