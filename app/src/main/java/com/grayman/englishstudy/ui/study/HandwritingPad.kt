package com.grayman.englishstudy.ui.study

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.recognition.Ink

/**
 * The writing pad, ported from Word Pocket.
 * - Undo one stroke, or rub out strokes with the eraser.
 * - The pen's eraser end or side button erases too.
 * - Once a pen has touched the pad, finger (palm) touches are ignored.
 */
@SuppressLint("ViewConstructor")
class HandwritingPad(context: Context, inkColor: Int, lineColor: Int, private val onChanged: () -> Unit) : View(context) {
    private class Stroke {
        val path = Path()
        val points = mutableListOf<FloatArray>()
        val times = mutableListOf<Long>()

        fun add(x: Float, y: Float, time: Long) {
            if (points.isEmpty()) path.moveTo(x, y) else path.lineTo(x, y)
            points += floatArrayOf(x, y)
            times += time
        }
    }

    private val density = context.resources.displayMetrics.density
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = density
    }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    private val strokes = mutableListOf<Stroke>()
    private var active: Stroke? = null
    var eraserMode = false
    private var erasingNow = false
    private var stylusSeen = false
    private var eraserX = -1f
    private var eraserY = -1f

    private val eraserRadius get() = 18 * density

    val hasInk: Boolean get() = strokes.isNotEmpty() && active == null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spacing = 72 * density
        var y = spacing
        while (y < height) {
            canvas.drawLine(20 * density, y, width - 20 * density, y, linePaint)
            y += spacing
        }
        strokes.forEach { canvas.drawPath(it.path, inkPaint) }
        if (erasingNow && eraserX >= 0) canvas.drawCircle(eraserX, eraserY, eraserRadius, eraserPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val tool = event.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
            stylusSeen = true
        } else if (stylusSeen && tool == MotionEvent.TOOL_TYPE_FINGER) {
            return true // A palm resting on the screen while writing with the pen.
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            erasingNow = eraserMode || tool == MotionEvent.TOOL_TYPE_ERASER ||
                (event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return if (erasingNow) erase(event, action) else draw(event, action)
    }

    private fun draw(event: MotionEvent, action: Int): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                active = Stroke().also {
                    it.add(event.x, event.y, event.eventTime)
                    strokes += it
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = active ?: return false
                for (i in 0 until event.historySize) {
                    stroke.add(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                }
                stroke.add(event.x, event.y, event.eventTime)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                active?.add(event.x, event.y, event.eventTime)
                active = null
                invalidate()
                onChanged()
                return true
            }
        }
        invalidate()
        return true
    }

    private fun erase(event: MotionEvent, action: Int): Boolean {
        eraserX = event.x
        eraserY = event.y
        var removed = false
        if (action == MotionEvent.ACTION_MOVE) {
            for (i in 0 until event.historySize) removed = eraseAt(event.getHistoricalX(i), event.getHistoricalY(i)) || removed
        }
        removed = eraseAt(eraserX, eraserY) || removed
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            erasingNow = false
            eraserX = -1f
            eraserY = -1f
        }
        invalidate()
        if (removed) onChanged()
        return true
    }

    private fun eraseAt(x: Float, y: Float): Boolean {
        val r2 = eraserRadius * eraserRadius
        val before = strokes.size
        strokes.removeAll { stroke -> stroke.points.any { (px, py) -> (px - x) * (px - x) + (py - y) * (py - y) <= r2 } }
        return strokes.size != before
    }

    /** Removes the last stroke; false when there is none. */
    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        invalidate()
        onChanged()
        return true
    }

    fun clear() {
        if (strokes.isEmpty() && active == null) return
        strokes.clear()
        active = null
        invalidate()
        onChanged()
    }

    fun ink(): Ink {
        val builder = Ink.builder()
        strokes.forEach { stroke ->
            val s = Ink.Stroke.builder()
            stroke.points.forEachIndexed { i, (x, y) -> s.addPoint(Ink.Point.create(x, y, stroke.times[i])) }
            builder.addStroke(s.build())
        }
        return builder.build()
    }
}
