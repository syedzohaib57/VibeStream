package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.use
import com.example.streamingappzb.R

/**
 * The projector halo (PRD §5 `Rosette`) that sits behind the hero, the title backdrop and
 * the player frame when a title has no key art.
 *
 * Drawn rather than an ImageView on `@drawable/rosette` because the design lets it
 * overhang its container — `left: 64%, marginLeft: -160` on a 320dp halo — and a view
 * that big would otherwise have to be laid out outside its parent's bounds.
 */
class RosetteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Halo diameter. May exceed the view's own size; the parent clips it. */
    var haloSize: Float = resources.getDimension(R.dimen.title_rosette)
        set(value) {
            field = value
            invalidate()
        }

    /** Centre as a fraction of the view, matching the design's percentage offsets. */
    var centreXFraction: Float = 0.64f
        set(value) {
            field = value
            invalidate()
        }

    var centreYFraction: Float = 0.30f
        set(value) {
            field = value
            invalidate()
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.RosetteView).use { a ->
            haloSize = a.getDimension(
                R.styleable.RosetteView_mhHaloSize,
                resources.getDimension(R.dimen.title_rosette),
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        MotifPainter.drawRosette(
            canvas,
            cx = width * centreXFraction,
            cy = height * centreYFraction,
            diameter = haloSize,
            colours = MotifColours.of(context),
        )
    }
}
