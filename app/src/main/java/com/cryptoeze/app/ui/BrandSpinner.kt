package com.cryptoeze.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.cryptoeze.app.R
import kotlin.math.cos
import kotlin.math.sin

/** Brand loader: a teal arc that breathes while it spins, led by the gold dot. */
class BrandSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val painter = Painter(context)

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        painter.draw(canvas, width / 2f, height / 2f, r, SystemClock.uptimeMillis())
        if (isShown) postInvalidateOnAnimation()
    }

    class Painter(context: Context) {
        private val density = context.resources.displayMetrics.density
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.brand_teal)
            alpha = 40
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = ContextCompat.getColor(context, R.color.brand_teal)
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.brand_gold)
        }
        private val oval = RectF()

        fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, now: Long, alpha: Float = 1f) {
            val stroke = maxOf(2.5f * density, radius * 0.16f)
            val r = radius - stroke
            track.strokeWidth = stroke
            arc.strokeWidth = stroke
            track.alpha = (40 * alpha).toInt()
            arc.alpha = (255 * alpha).toInt()
            dot.alpha = (255 * alpha).toInt()

            val t = (now % 1400L) / 1400f
            val rotation = (now % 1100L) / 1100f * 360f
            // Sweep grows then shrinks each cycle (ease in-out).
            val phase = if (t < 0.5f) t * 2 else (1 - t) * 2
            val eased = phase * phase * (3 - 2 * phase)
            val sweep = 30f + 230f * eased
            val start = rotation + t * 180f

            oval.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawCircle(cx, cy, r, track)
            canvas.drawArc(oval, start, sweep, false, arc)
            val head = Math.toRadians((start + sweep).toDouble())
            canvas.drawCircle(cx + r * cos(head).toFloat(), cy + r * sin(head).toFloat(), stroke * 0.95f, dot)
        }
    }
}
