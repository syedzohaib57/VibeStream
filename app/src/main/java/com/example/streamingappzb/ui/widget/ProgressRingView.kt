package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.use
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dp

/**
 * The download progress ring (TitleScreen.jsx `DownloadGlyph`):
 *
 * ```css
 * background: conic-gradient(mh-teal-400 <pct * 3.6>deg, mh-night-500 0deg);
 * ```
 * plus an inset circle of night900 punched out of the middle.
 *
 * A two-stop conic gradient is exactly an arc, so this draws arcs rather than building a
 * SweepGradient — crisper at small sizes and cheaper to animate.
 *
 * This is one of the two places a gradient could not be shipped as a drawable: it changes
 * every progress tick.
 */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val bounds = RectF()

    private var stroke: Float = context.dp(3).toFloat()
    private var ringColour: Int = context.color(R.color.cyan400)
    private var trackColour: Int = context.color(R.color.night500)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    /** 0..100. */
    var percent: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, 100)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ProgressRingView).use { a ->
            stroke = a.getDimension(R.styleable.ProgressRingView_mhRingStroke, stroke)
            ringColour = a.getColor(R.styleable.ProgressRingView_mhRingColor, ringColour)
            trackColour = a.getColor(R.styleable.ProgressRingView_mhRingTrackColor, trackColour)
        }
        paint.strokeWidth = stroke
    }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2f
        bounds.set(inset, inset, width - inset, height - inset)

        paint.color = trackColour
        canvas.drawArc(bounds, 0f, FULL_CIRCLE, false, paint)

        if (percent > 0) {
            paint.color = ringColour
            // From twelve o'clock, like the CSS conic gradient.
            canvas.drawArc(bounds, START_ANGLE, FULL_CIRCLE * percent / 100f, false, paint)
        }
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val START_ANGLE = -90f
    }
}
