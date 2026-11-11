package com.grayman.speakdaily.ui.listening

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grayman.speakdaily.app
import com.grayman.speakdaily.data.Level
import com.grayman.speakdaily.data.Phrase
import com.grayman.speakdaily.data.listeningRate
import com.grayman.speakdaily.domain.ListeningScorer
import com.grayman.speakdaily.domain.ListeningTestBuilder
import com.grayman.speakdaily.domain.TestPage
import com.grayman.speakdaily.domain.TestPart
import com.grayman.speakdaily.ui.Chip
import com.grayman.speakdaily.ui.ChunkyButton
import com.grayman.speakdaily.ui.Confetti
import com.grayman.speakdaily.ui.Game
import com.grayman.speakdaily.ui.GameProgressBar
import com.grayman.speakdaily.ui.Mascot
import com.grayman.speakdaily.ui.MascotMood
import com.grayman.speakdaily.ui.SecondaryButton
import com.grayman.speakdaily.ui.SpeechBubble
import kotlinx.coroutines.launch

private const val XP_PER_CORRECT = 3
private const val XP_FINISH = 10

private fun TestSession.isCorrect(page: Int, question: Int): Boolean {
    val chosen = answers[key(page, question)]
    return when (val p = pages[page]) {
        is TestPage.Response -> chosen == p.item.answer
        is TestPage.Set -> chosen == p.set.questions[question].answer
    }
}

private fun TestSession.report(): ListeningScorer.Report = ListeningScorer.report(
    pages.flatMapIndexed { pageIndex, page ->
        (0 until page.questionCount).map { q ->
            // A set's questions are about both speakers, so they count toward the main voice.
            ListeningScorer.Answered(page.part, voices[pageIndex].main.labelKo, isCorrect(pageIndex, q))
        }
    },
)

/** Missed response items become review cards: hearing the right reply again is the fix. */
private fun TestSession.missedPhrases(): List<Phrase> = pages.mapIndexedNotNull { index, page ->
    if (page is TestPage.Response && !isCorrect(index, 0)) {
        val right = page.item.options[page.item.answer]
        Phrase(right.text, "${page.item.promptKo} → ${right.ko}")
    } else {
        null
    }
}

@Composable
fun ListeningResult(session: TestSession, onExit: () -> Unit, onRetry: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val report = remember { session.report() }
    val missed = remember { session.missedPhrases() }
    val xp = report.total.correct * XP_PER_CORRECT + XP_FINISH
    LaunchedEffect(Unit) {
        app.progress.saveListeningTest(
            level = session.level,
            correct = report.total.correct,
            total = report.total.total,
            itemIds = ListeningTestBuilder.pageIds(session.pages),
            xp = xp,
            missedPhrases = missed,
        )
    }
    // One player for every review card, so replaying two questions never mixes their audio.
    val player = remember { PagePlayer(app.voice, session.level.listeningRate()) }
    val percent = report.total.percent
    val advice = ListeningScorer.advice(report.total)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Mascot(
                when {
                    percent >= 70 -> MascotMood.HAPPY
                    percent >= 50 -> MascotMood.IDLE
                    else -> MascotMood.SAD
                },
                size = 140.dp,
            )
            Text(
                "${report.total.correct} / ${report.total.total}",
                style = MaterialTheme.typography.headlineLarge,
                color = percentColor(percent),
            )
            Text(
                "${session.level.code.uppercase()} 듣기 · 정답률 $percent% · +$xp XP",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdviceBubble(session.level, advice)
            if (missed.isNotEmpty()) {
                SpeechBubble("놓친 질문-응답 ${missed.size}개를 복습 카드에 넣었어요.", Modifier.fillMaxWidth())
            }

            StatBlock("파트별", TestPart.entries.mapNotNull { part -> report.byPart[part]?.let { part.labelKo to it } })
            StatBlock("억양별", report.byAccent.entries.sortedBy { it.value.percent }.map { it.key to it.value })

            Text(
                "문제 다시 보기",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "눌러서 대본과 해석을 보고 다시 들어 보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            session.pages.indices.forEach { index -> ReviewCard(session, index, player) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("새 문제로 다시", icon = Icons.Rounded.Replay, onClick = onRetry, modifier = Modifier.weight(1f))
                ChunkyButton("완료", onClick = {
                    app.voice.stop()
                    onExit()
                }, modifier = Modifier.weight(1f))
            }
            if (advice == ListeningScorer.Advice.MOVE_UP && session.level != Level.C1) {
                val next = Level.entries[session.level.ordinal + 1]
                SecondaryButton(
                    "학습 레벨을 ${next.code.uppercase()}로 올리기",
                    onClick = { scope.launch { app.progress.setLevel(next) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (percent >= 70) Confetti()
    }
}

@Composable
private fun AdviceBubble(level: Level, advice: ListeningScorer.Advice) {
    val text = when {
        advice == ListeningScorer.Advice.MOVE_UP && level != Level.C1 ->
            "이 레벨은 충분해요! 다음엔 ${Level.entries[level.ordinal + 1].code.uppercase()} 테스트에 도전해 보세요."
        advice == ListeningScorer.Advice.MOVE_UP -> "최고 레벨에서도 훌륭해요! 실전 모드로 20문제에 도전해 보세요."
        advice == ListeningScorer.Advice.MOVE_DOWN && level != Level.A1 ->
            "조금 어려웠죠? ${Level.entries[level.ordinal - 1].code.uppercase()} 테스트로 귀를 먼저 익혀 봐요."
        advice == ListeningScorer.Advice.MOVE_DOWN -> "괜찮아요. 연습 모드로 여러 번 들으면서 익혀 봐요."
        else -> "이 레벨이 딱 맞아요. 틀린 문제를 다시 들으면 점수가 금방 올라요."
    }
    SpeechBubble(text, Modifier.fillMaxWidth())
}

@Composable
private fun StatBlock(title: String, rows: List<Pair<String, ListeningScorer.Tally>>) {
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        rows.forEach { (label, tally) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(104.dp),
                )
                GameProgressBar(tally.percent / 100f, Modifier.weight(1f), color = percentColor(tally.percent), height = 10.dp)
                Text(
                    "  ${tally.correct}/${tally.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(session: TestSession, index: Int, player: PagePlayer) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val page = session.pages[index]
    val pair = session.voices[index]
    val first = session.firstNumber(index)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button) { expanded = !expanded }
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (page.questionCount == 1) "$first" else "$first-${first + page.questionCount - 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                page.part.labelKo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            (0 until page.questionCount).forEach { q ->
                val right = session.isCorrect(index, q)
                Chip(
                    if (right) "O" else "X",
                    if (right) Game.GoodSoft else Game.BadSoft,
                    if (right) Game.OnGoodSoft else Game.OnBadSoft,
                )
            }
        }
        if (!expanded) return@Column

        SecondaryButton(
            "다시 듣기 (${pair.labelKo})",
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            onClick = { player.play(scope, page, pair) },
        )
        when (page) {
            is TestPage.Response -> {
                ScriptLine(page.item.prompt, page.item.promptKo)
                val chosen = session.answers[session.key(index, 0)]
                page.item.options.forEachIndexed { o, option ->
                    OptionReview("${LETTERS[o]}. ${option.text}", option.ko, o == page.item.answer, o == chosen)
                }
            }

            is TestPage.Set -> {
                page.set.lines.forEach { ScriptLine(it.text, it.ko) }
                page.set.questions.forEachIndexed { q, question ->
                    Text(
                        "${first + q}. ${question.q}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(question.qKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val chosen = session.answers[session.key(index, q)]
                    question.options.forEachIndexed { o, option ->
                        OptionReview("${LETTERS[o]}. $option", null, o == question.answer, o == chosen)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptLine(text: String, ko: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(ko, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionReview(text: String, ko: String?, isAnswer: Boolean, isChosen: Boolean) {
    val (background, content) = when {
        isAnswer -> Game.GoodSoft to Game.OnGoodSoft
        isChosen -> Game.BadSoft to Game.OnBadSoft
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text + when {
                isAnswer && isChosen -> "  (내 답 · 정답)"
                isAnswer -> "  (정답)"
                isChosen -> "  (내 답)"
                else -> ""
            },
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            textAlign = TextAlign.Start,
        )
        if (ko != null) Text(ko, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}
