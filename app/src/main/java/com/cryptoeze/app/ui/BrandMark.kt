package com.cryptoeze.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The CryptoEze mark drawn in code, on the same 1024 grid as tools/branding/generate.py,
 * so the animated intro matches the launcher icon and splash pixel for pixel.
 */
object BrandMark {
    const val GRID = 1024f
    private const val C = 512f
    private const val R_OUT = 212f
    private const val R_IN = 155f
    private const val CUT = 91f
    const val DOT_X = 682f
    const val DOT_R = 43f
    const val TILE_START = 140f
    const val TILE_END = 884f
    private const val TILE_RADIUS = 160f

    val ring: Path = Path().apply {
        val outerAngle = Math.toDegrees(atan2(CUT.toDouble(), sqrt((R_OUT * R_OUT - CUT * CUT).toDouble()))).toFloat()
        val innerAngle = Math.toDegrees(asin((CUT / R_IN).toDouble())).toFloat()
        val outer = RectF(C - R_OUT, C - R_OUT, C + R_OUT, C + R_OUT)
        val inner = RectF(C - R_IN, C - R_IN, C + R_IN, C + R_IN)
        // Outer edge: from the top of the opening, counter-clockwise round to the bottom.
        arcTo(outer, -outerAngle, -(360f - 2 * outerAngle), true)
        // Inner edge: back round the other way, then close across the top cut.
        arcTo(inner, innerAngle, 360f - 2 * innerAngle)
        close()
    }

    val tile: Path = Path().apply {
        addRoundRect(RectF(TILE_START, TILE_START, TILE_END, TILE_END), TILE_RADIUS, TILE_RADIUS, Path.Direction.CW)
    }

    /** Draws the mark centred on (cx, cy) with the tile [tileSize] px wide. */
    fun draw(
        canvas: Canvas, cx: Float, cy: Float, tileSize: Float,
        tilePaint: Paint?, ringPaint: Paint, dotPaint: Paint, dotScale: Float = 1f,
    ) {
        val s = tileSize / (TILE_END - TILE_START)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        canvas.translate(-C, -C)
        tilePaint?.let { canvas.drawPath(tile, it) }
        canvas.drawPath(ring, ringPaint)
        canvas.drawCircle(DOT_X, C, DOT_R * dotScale, dotPaint)
        canvas.restore()
    }
}
