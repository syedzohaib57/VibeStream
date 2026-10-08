package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dp

/**
 * Up next's 8-second countdown (FR-206 / PlayerScreen.jsx):
 *
 * ```css
 * background: radial-gradient(circle, mh-night-800 0 58%, transparent 60%),
 *             conic-gradient(mh-gold-500 <upNext/8 * 360>deg, mh-night-500 0deg);
 * ```
 *
 * An ember sweep over `night500` with the remaining seconds in the middle. The number uses
 * tabular figures so it does not shift as it counts 8 down to 0 (PRD §2.2).
 */
class CountdownRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val bounds = RectF()
    private val stroke = resources.getDimension(R.dimen.upnext_ring_stroke)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.BUTT
    }

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.color(R.color.night800)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.text_hi)
        textAlign = Paint.Align.CENTER
        textSize = context.dp(14).toFloat()
        // Tabular figures: the digit must not jump width as the count changes.
        fontFeatureSettings = "tnum"
        typeface = ResourcesCompat.getFont(context, R.font.inter_extrabold) ?: Typeface.DEFAULT_BOLD
    }

    private val trackColour = context.color(R.color.night500)
    private val ringColour = context.color(R.color.ember500)

    /** Total seconds the countdown started from. */
    var total: Int = TOTAL_DEFAULT
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    /** Seconds still to go. */
    var remaining: Int = TOTAL_DEFAULT
        set(value) {
            val clamped = value.coerceIn(0, total)
            if (field == clamped) return
            field = clamped
            contentDescription = resources.getQuantityString(
                R.plurals.upnext_seconds,
                clamped,
                clamped,
            )
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2f
        bounds.set(inset, inset, width - inset, height - inset)

        arcPaint.color = trackColour
        canvas.drawArc(bounds, 0f, FULL_CIRCLE, false, arcPaint)

        val fraction = remaining.toFloat() / total
        if (fraction > 0f) {
            arcPaint.color = ringColour
            canvas.drawArc(bounds, START_ANGLE, FULL_CIRCLE * fraction, false, arcPaint)
        }

        // The design's inner disc sits at 58% of the radius.
        canvas.drawCircle(width / 2f, height / 2f, (width / 2f) * DISC_FRACTION, discPaint)

        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(remaining.toString(), width / 2f, baseline, textPaint)
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val START_ANGLE = -90f
        const val DISC_FRACTION = 0.58f
        const val TOTAL_DEFAULT = 8
    }
}
