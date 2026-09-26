package com.cryptoeze.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.cryptoeze.app.R

/**
 * Animated brand intro that takes over seamlessly from the system splash screen:
 * the mark settles upward, CRYPTOEZE reveals letter by letter, then the brand loader
 * spins until the first page is ready and the whole layer dissolves into the app.
 */
class IntroView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val bgColor = ContextCompat.getColor(context, R.color.app_bg)
    private val tilePaint = paint(R.color.brand_ink)
    private val ringPaint = paint(R.color.brand_teal)
    private val dotPaint = paint(R.color.brand_gold)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.montserrat_extrabold)
        textSize = 30f * density
    }
    private val inkColor = ContextCompat.getColor(context, R.color.text_primary)
    private val tealColor = ContextCompat.getColor(context, R.color.brand_teal)
    private val spinner = BrandSpinner.Painter(context)

    private val word = "CRYPTOEZE"
    private val tracking = 3.2f * density
    private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)

    // Tile size the system splash icon renders at (288dp canvas, tile = 744/1024 * 0.0765 * 108 units).
    private val splashTile = 152f * density
    private val finalTile = 104f * density

    private var startTime = 0L
    private var ready = false
    private var exiting = false
    private var exitProgress = 0f
    private var onFinished: (() -> Unit)? = null

    fun start() {
        startTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** Call when the first page is ready; the intro leaves once its own reveal has played. */
    fun finish(onDone: () -> Unit) {
        if (ready) return
        ready = true
        onFinished = onDone
        postInvalidateOnAnimation()
    }

    /** Skip the intro entirely (e.g. activity re-created after a theme change). */
    fun dismissImmediately() {
        visibility = GONE
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
    }

    private fun progress(elapsed: Long, delay: Long, duration: Long): Float =
        emphasized.getInterpolation(((elapsed - delay).toFloat() / duration).coerceIn(0f, 1f))

    override fun onDraw(canvas: Canvas) {
        if (startTime == 0L) start()
        val elapsed = SystemClock.uptimeMillis() - startTime

        if (ready && !exiting && elapsed >= MIN_DURATION) startExit()

        val fade = 1f - exitProgress
        canvas.drawColor(bgColor)
        val alpha = (255 * fade).toInt()

        val cx = width / 2f
        val cy = height / 2f
        val settle = progress(elapsed, 120, 650)

        val tile = splashTile + (finalTile - splashTile) * settle
        val textHeight = textPaint.textSize
        val groupHeight = finalTile + 30f * density + textHeight
        val markCy = cy + (-(groupHeight / 2f) + finalTile / 2f) * settle
        val zoom = 1f + 0.06f * exitProgress

        canvas.save()
        canvas.scale(zoom, zoom, cx, cy)

        // Dot "heartbeat" once the mark lands.
        val pulseT = ((elapsed - 520).toFloat() / 420f).coerceIn(0f, 1f)
        val dotScale = 1f + 0.28f * kotlin.math.sin(pulseT * Math.PI).toFloat()
        tilePaint.alpha = alpha; ringPaint.alpha = alpha; dotPaint.alpha = alpha
        BrandMark.draw(canvas, cx, markCy, tile, tilePaint, ringPaint, dotPaint, dotScale)

        // Wordmark, letter by letter.
        val baseline = markCy + finalTile / 2f + 30f * density + textHeight * 0.72f
        val widths = FloatArray(word.length)
        textPaint.getTextWidths(word, widths)
        var x = cx - (widths.sum() + tracking * (word.length - 1)) / 2f
        for (i in word.indices) {
            val p = progress(elapsed, 420L + i * 45L, 520)
            textPaint.color = if (i < 6) inkColor else tealColor
            textPaint.alpha = (255 * p * fade).toInt()
            canvas.drawText(word, i, i + 1, x, baseline + (1f - p) * 14f * density, textPaint)
            x += widths[i] + tracking
        }
        canvas.restore()

        // Loader appears only if loading takes longer than the reveal.
        val spinnerIn = progress(elapsed, 1150, 400)
        if (spinnerIn > 0f) {
            spinner.draw(canvas, cx, baseline + 64f * density, 13f * density,
                SystemClock.uptimeMillis(), spinnerIn * fade)
        }

        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun startExit() {
        exiting = true
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                exitProgress = it.animatedValue as Float
                alpha = 1f - exitProgress
            }
            doOnEnd {
                visibility = GONE
                onFinished?.invoke()
            }
            start()
        }
        onFinished?.let { callback ->
            // Let the app content start fading in underneath while the intro dissolves.
            onFinished = null
            callback()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    companion object {
        private const val MIN_DURATION = 1350L
    }
}
