package com.example.streamingappzb.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen

/**
 * The design's switch (PRD §5 `MhToggle`): a 44x26 track that turns ember when on, with a
 * 20dp knob sliding over 160 ms.
 *
 * The view is a full 44dp tall so the touch target meets the minimum (PRD §2.3) while the
 * track stays the 26dp the design draws.
 *
 * Reports itself to TalkBack as a Switch with a checked state, so it reads exactly like a
 * platform switch despite being custom-drawn.
 */
class MhToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()

    private val trackWidth = context.dimen(R.dimen.toggle_width)
    private val trackHeight = context.dimen(R.dimen.toggle_height)
    private val knobSize = context.dimen(R.dimen.toggle_knob)
    private val inset = context.dimen(R.dimen.toggle_inset)

    private val offTrack = context.color(R.color.night500)
    private val onTrack = context.color(R.color.ember500)
    private val offKnob = context.color(R.color.text_mid)
    private val onKnob = context.color(R.color.text_on_ember)

    private var animator: ValueAnimator? = null
    private var listener: ((Boolean) -> Unit)? = null
    private var checked = false

    /** 0 = off, 1 = on — both the knob position and the colour blend. */
    private var fraction = 0f
        set(value) {
            field = value
            invalidate()
        }

    var isChecked: Boolean
        get() = checked
        set(value) = setChecked(value, animate = true, notify = false)

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = trackWidth
        minimumHeight = context.dimen(R.dimen.touch_min)

        // Announced as a platform Switch with a checked state, so TalkBack reads it
        // exactly like one despite being custom-drawn (PRD §8). Via the Compat node,
        // because AccessibilityNodeInfo's own setters are deprecated.
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Switch"
                    info.isCheckable = true
                    info.isChecked = checked
                }
            },
        )
    }

    /**
     * Binds state into the view without animating or notifying — used when a flow emits,
     * so observing your own write cannot loop.
     */
    fun setCheckedSilently(value: Boolean) = setChecked(value, animate = false, notify = false)

    fun setOnCheckedChangeListener(block: (Boolean) -> Unit) {
        listener = block
    }

    private fun setChecked(value: Boolean, animate: Boolean, notify: Boolean) {
        if (checked == value) return
        checked = value
        if (animate) {
            animateTo(value)
        } else {
            animator?.cancel()
            fraction = if (value) 1f else 0f
        }
        if (notify) listener?.invoke(value)
    }

    override fun performClick(): Boolean {
        setChecked(!checked, animate = true, notify = true)
        return super.performClick()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(trackWidth, widthMeasureSpec),
            resolveSize(minimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val left = (width - trackWidth) / 2f
        trackRect.set(left, cy - trackHeight / 2f, left + trackWidth, cy + trackHeight / 2f)

        trackPaint.color = blend(offTrack, onTrack, fraction)
        val radius = trackHeight / 2f
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val travel = trackWidth - knobSize - inset * 2
        val knobLeft = trackRect.left + inset + travel * fraction
        knobPaint.color = blend(offKnob, onKnob, fraction)
        canvas.drawCircle(knobLeft + knobSize / 2f, cy, knobSize / 2f, knobPaint)
    }

    private fun animateTo(value: Boolean) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(fraction, if (value) 1f else 0f).apply {
            duration = KNOB_DURATION_MS
            interpolator = MH_EASE
            addUpdateListener { fraction = it.animatedValue as Float }
            start()
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f).toInt() and 0xFF
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** The design's `transition: left 160ms`. */
        const val KNOB_DURATION_MS = 160L

        /** mhEase, PRD §2.3. */
        val MH_EASE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}
