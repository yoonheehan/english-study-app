package com.grayman.speakdaily.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MascotMood { IDLE, HAPPY }

private val Body = Color(0xFFFF7A59)
private val BodyDark = Color(0xFFE45F3E)
private val Belly = Color(0xFFFFE8D6)
private val Wing = Color(0xFF12A594)
private val Beak = Color(0xFFFFC233)
private val BeakDark = Color(0xFFE8A200)
private val Ink = Color(0xFF1F2A33)

/**
 * Echo, the app's own character: a round parrot, because parrots repeat what they
 * hear, which is what shadowing asks of the learner. Drawn in code, so no image assets.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val bounceAmount = if (mood == MascotMood.HAPPY) 0.09f else 0.025f
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == MascotMood.HAPPY) 420 else 1300, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val lift = -bounce * bounceAmount * s
        translateY(lift) {
            drawFeet(s)
            drawBird(s, mood, bounce)
        }
    }
}

private inline fun DrawScope.translateY(y: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.translate(0f, y)
    block()
    drawContext.canvas.restore()
}

private fun DrawScope.drawFeet(s: Float) {
    listOf(0.40f, 0.60f).forEach { x ->
        drawLine(Beak, Offset(s * x, s * 0.84f), Offset(s * x, s * 0.94f), s * 0.035f, StrokeCap.Round)
        drawLine(Beak, Offset(s * (x - 0.04f), s * 0.94f), Offset(s * (x + 0.04f), s * 0.94f), s * 0.035f, StrokeCap.Round)
    }
}

private fun DrawScope.drawBird(s: Float, mood: MascotMood, bounce: Float) {
    // Crest feathers
    val crest = listOf(-18f to Wing, 0f to Beak, 18f to BodyDark)
    crest.forEach { (angle, color) ->
        rotate(angle, pivot = Offset(s * 0.5f, s * 0.2f)) {
            drawOval(color, Offset(s * 0.455f, s * 0.04f), Size(s * 0.09f, s * 0.2f))
        }
    }

    // Body and belly
    drawCircle(Body, s * 0.36f, Offset(s * 0.5f, s * 0.52f))
    drawOval(Belly, Offset(s * 0.28f, s * 0.46f), Size(s * 0.44f, s * 0.38f))

    // Wings lift when happy
    val lift = if (mood == MascotMood.HAPPY) 25f + bounce * 25f else 4f
    rotate(lift, pivot = Offset(s * 0.17f, s * 0.48f)) {
        drawOval(Wing, Offset(s * 0.09f, s * 0.46f), Size(s * 0.17f, s * 0.27f))
    }
    rotate(-lift, pivot = Offset(s * 0.83f, s * 0.48f)) {
        drawOval(Wing, Offset(s * 0.74f, s * 0.46f), Size(s * 0.17f, s * 0.27f))
    }

    // Eyes
    val leftEye = Offset(s * 0.38f, s * 0.38f)
    val rightEye = Offset(s * 0.62f, s * 0.38f)
    listOf(leftEye, rightEye).forEach { center ->
        drawCircle(Color.White, s * 0.095f, center)
        if (mood == MascotMood.HAPPY) {
            drawArc(
                color = Ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - s * 0.06f, center.y - s * 0.035f),
                size = Size(s * 0.12f, s * 0.11f),
                style = Stroke(s * 0.024f, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(Ink, s * 0.05f, center.copy(y = center.y + s * 0.012f))
            drawCircle(Color.White, s * 0.016f, Offset(center.x + s * 0.018f, center.y - s * 0.008f))
        }
    }
    // Beak opens when happy.
    val open = if (mood == MascotMood.HAPPY) s * 0.05f else 0f
    val lower = Path().apply {
        moveTo(s * 0.43f, s * 0.47f + open)
        lineTo(s * 0.57f, s * 0.47f + open)
        lineTo(s * 0.5f, s * 0.56f + open)
        close()
    }
    drawPath(lower, BeakDark)
    val upper = Path().apply {
        moveTo(s * 0.41f, s * 0.46f)
        lineTo(s * 0.59f, s * 0.46f)
        lineTo(s * 0.5f, s * 0.54f)
        close()
    }
    drawPath(upper, Beak)
}
