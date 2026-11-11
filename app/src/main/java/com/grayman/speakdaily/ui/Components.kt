package com.grayman.speakdaily.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Game-style button: a colored face on a darker edge that presses down when touched. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Game.Lagoon,
    edge: Color = Game.LagoonEdge,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val haptics = LocalHapticFeedback.current
    val face = if (enabled) color else MaterialTheme.colorScheme.surfaceVariant
    val under = if (enabled) edge else MaterialTheme.colorScheme.outline
    val label = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .height(56.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .offset(y = depth)
                .background(under, RoundedCornerShape(16.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .offset(y = if (pressed) depth else 0.dp)
                .background(face, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = label, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** A selectable answer tile that shows right and wrong after the answer is locked. */
@Composable
fun AnswerTile(
    text: String,
    selected: Boolean,
    revealCorrect: Boolean,
    revealWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        revealCorrect -> Game.Good
        revealWrong -> Game.Bad
        selected -> Game.Lagoon
        else -> MaterialTheme.colorScheme.outline
    }
    val background = when {
        revealCorrect -> Game.GoodSoft
        revealWrong -> Game.BadSoft
        selected -> Game.LagoonSoft
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        revealCorrect -> Game.OnGoodSoft
        revealWrong -> Game.OnBadSoft
        selected -> Game.LagoonEdge
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

@Composable
fun GameProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Game.Good, height: Dp = 14.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(), label = "progress")
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun Chip(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

/** Big microphone with a ring that follows the input volume. */
@Composable
fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val ringScale by animateFloatAsState(if (listening) 1.15f + level * 0.5f else 1f, label = "ring")
    Box(modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(100.dp)
                    .scale(ringScale)
                    .background(Game.Coral.copy(alpha = 0.25f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(100.dp)
                .scale(if (listening) 1f else pulse.value)
                .background(if (listening) Game.Coral else Game.Lagoon, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Filled.Stop else Icons.Rounded.Mic,
                contentDescription = if (listening) "말하기 끝내기" else "말하기 시작",
                tint = Color.White,
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

/** Falling paper celebration, drawn once on the results screen. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2800, easing = LinearEasing)) }
    val pieces = remember {
        val random = Random(7)
        val colors = listOf(Game.Coral, Game.Lagoon, Game.Flame, Game.Xp, Game.Good)
        List(60) {
            ConfettiPiece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.8f + random.nextFloat() * 0.8f,
                sway = random.nextFloat() * 0.06f,
                color = colors[it % colors.size],
                size = 6f + random.nextFloat() * 10f,
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        pieces.forEach { piece ->
            val local = ((t - piece.delay) * piece.speed).coerceAtLeast(0f)
            if (local <= 0f) return@forEach
            val x = (piece.x + kotlin.math.sin(local * 12f) * piece.sway) * size.width
            val y = local * size.height * 1.1f
            drawRect(
                piece.color.copy(alpha = (1f - t).coerceIn(0f, 1f).let { if (it > 0.25f) 1f else it * 4f }),
                Offset(x, y),
                Size(piece.size * density / 2f, piece.size * density),
            )
        }
    }
}

private class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val color: Color,
    val size: Float,
)

@Composable
fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun CenteredText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
}
