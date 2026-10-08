package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.use
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.ui.base.color

/**
 * The player's seek rail (PRD §6.3): a 4dp track at 18% white with an ember fill, a 14dp
 * `ember400` thumb over a 22% halo, and **3 x 8dp `rose400` ticks at the authored mid-roll
 * breaks**.
 *
 * The ticks come from the same [adBreakFractions] list the ad controller plays from, which
 * is what makes them line up exactly rather than approximately (acceptance item 8).
 *
 * The touch target is 28dp tall even though the track is 4dp, and while [isEnabled] is
 * false — during an ad — it neither scrubs nor responds, matching the design's dimmed,
 * inert controls.
 */
class SeekRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackHeightPx: Float = readDimension(
        context, attrs, R.styleable.SeekRailView_mhTrackHeight, R.dimen.seek_track_height,
    )
    private val thumbSizePx: Float = readDimension(
        context, attrs, R.styleable.SeekRailView_mhThumbSize, R.dimen.seek_thumb,
    )

    private val trackRect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.track_on_dark)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.ember500)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.rose400)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.ember400)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.ember_thumb_halo)
    }

    private val tickWidth = resources.getDimension(R.dimen.ad_tick_width)
    private val tickHeight = resources.getDimension(R.dimen.ad_tick_height)
    private val haloWidth = resources.getDimension(R.dimen.seek_thumb_halo)

    private var dragging = false
    private var position = 0

    /** Content duration in seconds. */
    var durationSeconds: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }

    /**
     * Current position in seconds.
     *
     * Writes are ignored while the viewer is dragging, so the player's 1 Hz position
     * updates cannot yank the thumb out from under their finger.
     */
    var positionSeconds: Int
        get() = position
        set(value) {
            if (!dragging) setPosition(value)
        }

    private fun setPosition(value: Int) {
        val clamped = value.coerceIn(0, durationSeconds)
        if (position == clamped) return
        position = clamped
        updateAccessibilityText()
        invalidate()
    }

    /** Authored mid-roll break positions, as 0..1 fractions of the runtime. */
    var adBreakFractions: List<Float> = emptyList()
        set(value) {
            field = value.filter { it > 0f && it < 1f }
            invalidate()
        }

    /** Fired continuously while dragging so the time code tracks the thumb. */
    var onScrub: ((Int) -> Unit)? = null

    /** Fired once, on release or on a tap. */
    var onSeek: ((Int) -> Unit)? = null

    init {
        minimumHeight = resources.getDimensionPixelSize(R.dimen.seek_touch_height)
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.cd_seek_bar)

        // Dragging is a gesture TalkBack cannot perform, so the rail also exposes
        // scroll-forward and scroll-backward actions — otherwise seeking would be
        // unreachable without sight (PRD §8: TalkBack reads *and drives* every control).
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.SeekBar"
                    if (!isEnabled || durationSeconds <= 0) return
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
                    info.rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                        AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_INT,
                        0f,
                        durationSeconds.toFloat(),
                        position.toFloat(),
                    )
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: android.os.Bundle?,
                ): Boolean {
                    if (!isEnabled || durationSeconds <= 0) {
                        return super.performAccessibilityAction(host, action, args)
                    }
                    val delta = when (action) {
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> ACCESSIBILITY_STEP_SECONDS
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> -ACCESSIBILITY_STEP_SECONDS
                        else -> return super.performAccessibilityAction(host, action, args)
                    }
                    val target = (position + delta).coerceIn(0, durationSeconds)
                    setPosition(target)
                    onSeek?.invoke(target)
                    return true
                }
            },
        )
    }

    private val fraction: Float
        get() = if (durationSeconds <= 0) 0f else positionSeconds.toFloat() / durationSeconds

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(minimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        // The thumb overhangs the track, so the track is inset by its radius to keep both
        // ends on screen.
        val thumbRadius = thumbSizePx / 2f
        val left = thumbRadius
        val right = width - thumbRadius
        if (right <= left) return

        val cy = height / 2f
        val radius = trackHeightPx / 2f
        trackRect.set(left, cy - radius, right, cy + radius)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val fillEnd = left + (right - left) * fraction
        if (fillEnd > left) {
            trackRect.set(left, cy - radius, fillEnd, cy + radius)
            canvas.drawRoundRect(trackRect, radius, radius, fillPaint)
        }

        for (breakFraction in adBreakFractions) {
            val x = left + (right - left) * breakFraction
            trackRect.set(
                x - tickWidth / 2f,
                cy - tickHeight / 2f,
                x + tickWidth / 2f,
                cy + tickHeight / 2f,
            )
            canvas.drawRoundRect(trackRect, TICK_RADIUS, TICK_RADIUS, tickPaint)
        }

        canvas.drawCircle(fillEnd, cy, thumbRadius + haloWidth, haloPaint)
        canvas.drawCircle(fillEnd, cy, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Inert during an ad (PRD §6.3).
        if (!isEnabled || durationSeconds <= 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                applyTouch(event.x, notify = true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    applyTouch(event.x, notify = true)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    applyTouch(event.x, notify = false)
                    onSeek?.invoke(positionSeconds)
                    performClick()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyTouch(x: Float, notify: Boolean) {
        val thumbRadius = thumbSizePx / 2f
        val span = (width - thumbSizePx).coerceAtLeast(1f)
        val f = ((x - thumbRadius) / span).coerceIn(0f, 1f)
        val seconds = (f * durationSeconds).toInt()
        val changed = seconds != position
        // Straight to the backing setter: the public one guards against writes while
        // dragging, which is exactly what this is.
        setPosition(seconds)
        if (changed && notify) onScrub?.invoke(seconds)
    }

    /**
     * Lint wants an explicit override alongside [onTouchEvent] so a custom view that
     * handles touches is still clickable for accessibility. The tap-to-seek path already
     * calls through here on ACTION_UP.
     */
    override fun performClick(): Boolean = super.performClick()

    private fun updateAccessibilityText() {
        contentDescription = context.getString(
            R.string.cd_seek_position,
            Format.time(positionSeconds),
            Format.time(durationSeconds),
        )
    }

    private companion object {
        const val TICK_RADIUS = 1f

        /** One TalkBack swipe moves the same 10 s the skip buttons do. */
        const val ACCESSIBILITY_STEP_SECONDS = 10
    }
}

/**
 * Reads one dimension attribute, falling back to a resource default.
 *
 * A standalone function rather than assignment inside `TypedArray.use {}`, because Kotlin
 * cannot see a `val` assigned inside a lambda as definitely initialised.
 */
private fun readDimension(
    context: Context,
    attrs: AttributeSet?,
    styleableIndex: Int,
    fallbackDimenRes: Int,
): Float = context.obtainStyledAttributes(attrs, R.styleable.SeekRailView).use { a ->
    a.getDimension(styleableIndex, context.resources.getDimension(fallbackDimenRes))
}
