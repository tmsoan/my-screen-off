package com.anos.myscreenoff.data

/** How the floating button looks and behaves. Edited in the app, applied by the service. */
data class ButtonSettings(
    val sizeDp: Float = 56f,
    val color: Int = ButtonPalette.colors.first(),
    val opacity: Float = 0.9f,
    val fadeWhenIdle: Boolean = true,
    val idleOpacity: Float = 0.35f,
    val fadeDelayMs: Int = 3000,
    val lockDelayMs: Int = 0,
    val snapToEdge: Boolean = true,
    val haptic: Boolean = true,
) {
    companion object {
        const val MIN_SIZE_DP = 36f
        const val MAX_SIZE_DP = 96f
        const val MIN_OPACITY = 0.2f
        const val MIN_IDLE_OPACITY = 0.1f
        const val MIN_FADE_DELAY_MS = 1000
        const val MAX_FADE_DELAY_MS = 10000
        const val MAX_LOCK_DELAY_MS = 3000
    }
}

/**
 * Where the button sits, as fractions of the room it has to move in (0 = left/top, 1 = right/bottom),
 * so the spot survives rotation and size changes.
 */
data class ButtonPosition(val xFraction: Float = 1f, val yFraction: Float = 0.5f)

object ButtonPalette {
    val colors: List<Int> = listOf(
        0xFF1F2937, 0xFF000000, 0xFFFFFFFF, 0xFF9CA3AF, 0xFFEF4444, 0xFFF97316,
        0xFFFFC928, 0xFF22C55E, 0xFF14B8A6, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899,
    ).map { it.toInt() }
}
