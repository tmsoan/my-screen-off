package com.anos.myscreenoff.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.anos.myscreenoff.R
import com.anos.myscreenoff.data.ButtonPosition
import com.anos.myscreenoff.data.ButtonSettings
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The draggable lock button and the dismiss target shown while it is dragged, both drawn as
 * accessibility overlays. [context] must be the accessibility service.
 */
@SuppressLint("ClickableViewAccessibility") // DragListener calls performClick for taps.
class FloatingButton(
    private val context: Context,
    private var position: ButtonPosition,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onMoved: (ButtonPosition) -> Unit,
    private val onDismissed: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private var settings = ButtonSettings()
    private var shown = false
    private var targetShown = false
    private var overTarget = false
    /** True once the current touch has been held long enough to count as a long press. */
    private var heldLong = false
    private var snapAnimator: ValueAnimator? = null

    private val buttonBackground = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private val button = ImageView(context).apply {
        background = buttonBackground
        setImageResource(R.drawable.ic_lock)
        contentDescription = context.getString(R.string.lock_screen)
        setOnClickListener {
            if (settings.haptic) it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            scaleX = TAP_SCALE
            scaleY = TAP_SCALE
            animate().scaleX(1f).scaleY(1f).setDuration(TAP_ANIMATION_MS)
            onTap()
        }
    }

    private val target = ImageView(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(TARGET_COLOR)
        }
        setImageResource(R.drawable.ic_close)
    }

    private val buttonParams = overlayParams(flags = 0).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private val targetParams = overlayParams(flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }

    private val fade = Runnable {
        button.animate().alpha(min(settings.opacity, settings.idleOpacity)).setDuration(FADE_ANIMATION_MS)
    }

    private val longPress = Runnable {
        heldLong = true
        if (settings.haptic) button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress()
    }

    init {
        button.setOnTouchListener(DragListener())
    }

    fun apply(settings: ButtonSettings) {
        this.settings = settings
        buttonBackground.setColor(settings.color)
        buttonBackground.setStroke(dp(1f), if (isLight(settings.color)) STROKE_ON_LIGHT else STROKE_ON_DARK)
        button.imageTintList = ColorStateList.valueOf(if (isLight(settings.color)) Color.BLACK else Color.WHITE)
        if (settings.snapToEdge) position = position.copy(xFraction = ButtonGeometry.snap(position.xFraction))
        layout()
        wake()
    }

    fun show() {
        if (shown) return
        layout()
        windowManager.addView(button, buttonParams)
        shown = true
        wake()
    }

    fun hide() {
        handler.removeCallbacks(fade)
        handler.removeCallbacks(longPress)
        snapAnimator?.cancel()
        hideTarget()
        if (!shown) return
        windowManager.removeView(button)
        shown = false
    }

    /** Call when the screen rotates or changes size, to put the button back at its saved spot. */
    fun onScreenChanged() {
        snapAnimator?.cancel()
        layout()
    }

    /** Sizes the button and places it at [position] within the current screen area. */
    private fun layout() {
        val size = sizePx()
        val margin = dp(EDGE_MARGIN_DP)
        val (areaWidth, areaHeight) = area()
        val padding = (size * ICON_PADDING_FRACTION).roundToInt()
        button.setPadding(padding, padding, padding, padding)
        // At the screen edge the button sits in the back-gesture zone; without this, dragging it
        // away from the edge goes back in the app underneath instead.
        button.systemGestureExclusionRects = listOf(Rect(0, 0, size, size))
        buttonParams.width = size
        buttonParams.height = size
        buttonParams.x = ButtonGeometry.position(position.xFraction, areaWidth, size, margin)
        buttonParams.y = ButtonGeometry.position(position.yFraction, areaHeight, size, margin)
        if (shown) windowManager.updateViewLayout(button, buttonParams)
    }

    /** Restores full opacity and restarts the idle countdown. */
    private fun wake() {
        handler.removeCallbacks(fade)
        button.animate().cancel()
        button.scaleX = 1f
        button.scaleY = 1f
        button.alpha = settings.opacity
        if (shown && settings.fadeWhenIdle) handler.postDelayed(fade, settings.fadeDelayMs.toLong())
    }

    private fun moveTo(x: Int, y: Int) {
        buttonParams.x = x
        buttonParams.y = y
        if (shown) windowManager.updateViewLayout(button, buttonParams)
    }

    /** Saves where the button was dropped and slides it to the edge it is pinned to. */
    private fun settle() {
        val size = sizePx()
        val margin = dp(EDGE_MARGIN_DP)
        val (areaWidth, areaHeight) = area()
        val xFraction = ButtonGeometry.fraction(buttonParams.x, areaWidth, size, margin)
        position = ButtonPosition(
            xFraction = if (settings.snapToEdge) ButtonGeometry.snap(xFraction) else xFraction,
            yFraction = ButtonGeometry.fraction(buttonParams.y, areaHeight, size, margin),
        )
        onMoved(position)

        val endX = ButtonGeometry.position(position.xFraction, areaWidth, size, margin)
        snapAnimator = ValueAnimator.ofInt(buttonParams.x, endX).apply {
            duration = SNAP_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { moveTo(it.animatedValue as Int, buttonParams.y) }
            start()
        }
    }

    private fun showTarget() {
        if (targetShown) return
        val size = dp(TARGET_SIZE_DP)
        val padding = size / 4
        target.setPadding(padding, padding, padding, padding)
        target.scaleX = 1f
        target.scaleY = 1f
        targetParams.width = size
        targetParams.height = size
        targetParams.y = dp(TARGET_BOTTOM_MARGIN_DP)
        windowManager.addView(target, targetParams)
        targetShown = true
    }

    private fun hideTarget() {
        overTarget = false
        if (!targetShown) return
        target.animate().cancel()
        windowManager.removeView(target)
        targetShown = false
    }

    /** Tracks whether the dragged button is on the dismiss target, and shows it. */
    private fun updateOverTarget() {
        val (areaWidth, areaHeight) = area()
        val half = sizePx() / 2f
        val targetSize = dp(TARGET_SIZE_DP)
        val over = ButtonGeometry.isWithin(
            centerX = buttonParams.x + half,
            centerY = buttonParams.y + half,
            targetX = areaWidth / 2f,
            targetY = areaHeight - dp(TARGET_BOTTOM_MARGIN_DP) - targetSize / 2f,
            radius = targetSize / 2f + dp(TARGET_REACH_DP),
        )
        if (over == overTarget) return
        overTarget = over
        val scale = if (over) TARGET_ACTIVE_SCALE else 1f
        target.animate().scaleX(scale).scaleY(scale).setDuration(TAP_ANIMATION_MS)
        button.alpha = if (over) settings.opacity * OVER_TARGET_ALPHA else settings.opacity
    }

    /** The width and height the button can move in: the screen without system bars and cutouts. */
    private fun area(): Pair<Int, Int> {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(FIT_INSETS)
        return Pair(
            metrics.bounds.width() - insets.left - insets.right,
            metrics.bounds.height() - insets.top - insets.bottom,
        )
    }

    private fun sizePx(): Int = dp(settings.sizeDp)

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun isLight(color: Int): Boolean = Color.luminance(color) > 0.5f

    private fun overlayParams(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Keep the overlay clear of the bars and cutout even while an app hides them, so its
        // coordinates always match area().
        fitInsetsTypes = FIT_INSETS
        isFitInsetsIgnoringVisibility = true
    }

    /** Tells taps, long presses and drags apart, moves the button with the finger and handles the drop. */
    private inner class DragListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    handler.removeCallbacks(fade)
                    button.animate().cancel()
                    button.alpha = settings.opacity
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = buttonParams.x
                    startY = buttonParams.y
                    dragging = false
                    heldLong = false
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }

                MotionEvent.ACTION_MOVE -> {
                    // After a long press the rest of the touch is ignored, so it cannot also drag.
                    if (heldLong) return true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                        showTarget()
                    }
                    if (dragging) {
                        val size = sizePx()
                        val margin = dp(EDGE_MARGIN_DP)
                        val (areaWidth, areaHeight) = area()
                        moveTo(
                            ButtonGeometry.clamp(startX + dx.roundToInt(), areaWidth, size, margin),
                            ButtonGeometry.clamp(startY + dy.roundToInt(), areaHeight, size, margin),
                        )
                        updateOverTarget()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val released = event.actionMasked == MotionEvent.ACTION_UP
                    handler.removeCallbacks(longPress)
                    if (heldLong) {
                        wake()
                    } else if (dragging) {
                        dragging = false
                        val dismissed = released && overTarget
                        hideTarget()
                        if (dismissed) {
                            hide()
                            onDismissed()
                            return true
                        }
                        settle()
                        wake()
                    } else {
                        // performClick's own animation must outlive wake(), so wake first.
                        wake()
                        // A hold let go before the long press is neither, so it must not lock.
                        val tapped = event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                        if (released && tapped) view.performClick()
                    }
                }
            }
            return true
        }
    }

    private companion object {
        val FIT_INSETS = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()

        const val EDGE_MARGIN_DP = 6f
        const val ICON_PADDING_FRACTION = 0.26f
        const val TAP_SCALE = 0.85f
        const val TAP_ANIMATION_MS = 150L
        const val FADE_ANIMATION_MS = 400L
        const val SNAP_ANIMATION_MS = 200L
        /** How long the button is held to open the app. */
        const val LONG_PRESS_MS = 5_000L

        const val TARGET_SIZE_DP = 64f
        const val TARGET_BOTTOM_MARGIN_DP = 32f
        /** How far beyond the target's rim the button's center still counts as on it. */
        const val TARGET_REACH_DP = 28f
        const val TARGET_ACTIVE_SCALE = 1.25f
        const val OVER_TARGET_ALPHA = 0.5f
        val TARGET_COLOR = Color.argb(0xCC, 0x20, 0x20, 0x20)
        val STROKE_ON_DARK = Color.argb(0x55, 0xFF, 0xFF, 0xFF)
        val STROKE_ON_LIGHT = Color.argb(0x33, 0x00, 0x00, 0x00)
    }
}
