package com.anos.myscreenoff.service

import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pixel math for placing the button along one axis of the area it may move in. */
object ButtonGeometry {

    /** The room the button's top-left corner has to move in, never negative. */
    private fun travel(areaPx: Int, sizePx: Int, marginPx: Int): Int =
        (areaPx - sizePx - 2 * marginPx).coerceAtLeast(0)

    fun position(fraction: Float, areaPx: Int, sizePx: Int, marginPx: Int): Int =
        marginPx + (fraction.coerceIn(0f, 1f) * travel(areaPx, sizePx, marginPx)).roundToInt()

    fun fraction(positionPx: Int, areaPx: Int, sizePx: Int, marginPx: Int): Float {
        val travel = travel(areaPx, sizePx, marginPx)
        if (travel == 0) return 0f
        return ((positionPx - marginPx).toFloat() / travel).coerceIn(0f, 1f)
    }

    fun clamp(positionPx: Int, areaPx: Int, sizePx: Int, marginPx: Int): Int =
        positionPx.coerceIn(marginPx, marginPx + travel(areaPx, sizePx, marginPx))

    /** The nearer edge: 0 for left, 1 for right. */
    fun snap(xFraction: Float): Float = if (xFraction < 0.5f) 0f else 1f

    fun isWithin(centerX: Float, centerY: Float, targetX: Float, targetY: Float, radius: Float): Boolean =
        hypot(centerX - targetX, centerY - targetY) <= radius
}
