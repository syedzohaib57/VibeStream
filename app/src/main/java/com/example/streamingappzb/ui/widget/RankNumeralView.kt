package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color

/**
 * The rank numeral that sits behind a Top 10 poster (§6.1, ranked rows).
 *
 * The glyph is filled with the page black and stroked with a 2dp hairline, so against
 * night900 only the outline reads and the poster beside it appears to stand in front of a
 * cut-out.
 *
 * The size comes from the view's **height**, not an sp value: the numeral is art rather
 * than copy, so it must not grow with the font scale and push the row past its height
 * budget. The rank is announced by the poster's own content description instead — see
 * `HomeAdapter` — which is why this view is never read aloud on its own.
 */
class RankNumeralView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** 1..10. Anything outside that draws nothing and measures to zero width. */
    var rank: Int = 0
        set(value) {
            if (field == value) return
            field = value
            text = if (value in MIN_RANK..MAX_RANK) value.toString() else ""
            requestLayout()
            invalidate()
        }

    private var text = ""

    private val extrabold = ResourcesCompat.getFont(context, R.font.inter_extrabold)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.color(R.color.rank_numeral_fill)
        typeface = extrabold
        textAlign = Paint.Align.LEFT
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.color(R.color.rank_numeral_stroke)
        strokeWidth = resources.getDimension(R.dimen.rank_numeral_stroke_width)
        typeface = extrabold
        textAlign = Paint.Align.LEFT
    }

    /** The glyph's ink box, which is what gets aligned — not the font's line box. */
    private val ink = Rect()

    /** Negative: the poster's start margin in the ranked item layout. */
    private val posterOverlap = resources.getDimensionPixelSize(R.dimen.rank_poster_overlap)

    private val minVisibleWidth = resources.getDimensionPixelSize(R.dimen.rank_numeral_min_visible)

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The row gives this an exact height; the fallback keeps the preview and any
        // wrap_content use from collapsing the glyph to nothing.
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            resources.getDimensionPixelSize(R.dimen.poster_row_height)
        }

        fitGlyphTo(height)

        // Width hugs the ink, so "10" gets the room it needs and "7" does not reserve it.
        //
        // The floor is what keeps a narrow glyph readable. The poster beside this view is
        // pulled back over it by `rank_poster_overlap`, a fixed distance measured from this
        // view's right edge — which covers most of a "1" but little of a "2". Measuring at
        // least `overlap + min_visible` wide leaves every numeral the same clear sliver.
        val width = if (text.isEmpty()) {
            0
        } else {
            val ok = ink.width() + (strokePaint.strokeWidth * 2f).toInt()
            // The overlap dimen is negative, so subtracting it adds the covered width.
            maxOf(ok, minVisibleWidth - posterOverlap)
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), height)
    }

    /**
     * Scales the text so the ink box is [GLYPH_HEIGHT_FRACTION] of the row height.
     *
     * One pass is exact: glyph outlines scale linearly with text size, so measuring at a
     * probe size and multiplying by the shortfall lands on the target.
     */
    private fun fitGlyphTo(height: Int) {
        if (text.isEmpty()) {
            ink.setEmpty()
            return
        }
        fillPaint.textSize = PROBE_TEXT_SIZE
        fillPaint.getTextBounds(text, 0, text.length, ink)
        val measured = ink.height().toFloat()
        if (measured <= 0f) return

        val size = PROBE_TEXT_SIZE * (height * GLYPH_HEIGHT_FRACTION / measured)
        fillPaint.textSize = size
        strokePaint.textSize = size
        fillPaint.getTextBounds(text, 0, text.length, ink)
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return

        // Subtracting the left bearing puts the *ink* at the inset rather than the glyph's
        // advance origin, which for a "1" would otherwise leave a wide gap.
        val x = strokePaint.strokeWidth - ink.left
        // Baseline placed so the ink box bottom lands on the view's bottom edge.
        val y = height.toFloat() - ink.bottom

        canvas.drawText(text, x, y, fillPaint)
        canvas.drawText(text, x, y, strokePaint)
    }

    private companion object {
        const val MIN_RANK = 1
        const val MAX_RANK = 10

        /** Any size works; glyphs scale linearly, so this only needs to be precise. */
        const val PROBE_TEXT_SIZE = 100f

        /**
         * Slightly shorter than the poster beside it. Calibrated on a 1080x2400 phone:
         * at 0.92 the glyph read as taller than the card and each slot grew wide enough
         * that only two fitted on screen, against three in every unranked row.
         */
        const val GLYPH_HEIGHT_FRACTION = 0.84f
    }
}
