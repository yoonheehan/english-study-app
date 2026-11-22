package com.grayman.englishstudy.ui.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.ai.RolePlay
import com.grayman.englishstudy.ai.RolePlayTopic
import com.grayman.englishstudy.app
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.ai.AiModelCard

/** The speaking tab: pick a situation and talk with the AI. */
@Composable
fun RolePlayTab(onOpen: (String, String?) -> Unit) {
    val app = LocalContext.current.app
    val state by app.tutor.state.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 900.dp) 3 else if (maxWidth >= 600.dp) 2 else 1
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("말하기", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "상황을 고르면 AI가 상대역을 맡아요. 마이크로 대답하고, 끝나면 고칠 문장과 더 좋은 표현을 알려 줘요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AiModelCard()
            CustomTopicCard(enabled = state.downloaded, onStart = { onOpen("custom", it) }, onRandom = { onOpen(RolePlay.topics.random().id, null) })
            Text(
                "주제 ${RolePlay.topics.size}개 · 고를 때마다 상황, 상대, 돌발 상황이 달라져요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RolePlay.topics.groupBy { it.group }.forEach { (group, topics) ->
                Text(group, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                topics.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { TopicCard(it, enabled = state.downloaded, Modifier.weight(1f)) { onOpen(it.id, null) } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(topic: RolePlayTopic, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            topic.titleKo,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Game.LagoonEdge else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(topic.descriptionKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The learner's own situation, in Korean or English, or a random topic. */
@Composable
private fun CustomTopicCard(enabled: Boolean, onStart: (String) -> Unit, onRandom: () -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("내가 만든 상황", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("예: 회사 회식에서 외국인 상사에게 건배사 하기 / 중고거래 가격 흥정") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.grayman.englishstudy.ui.SecondaryButton("아무 주제나", onClick = { if (enabled) onRandom() }, modifier = Modifier.weight(1f))
            com.grayman.englishstudy.ui.ChunkyButton(
                "이 상황으로 시작",
                onClick = { onStart(text.trim()) },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
