package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.use
import androidx.core.view.isVisible
import coil.load
import coil.size.Scale
import com.example.streamingappzb.R
import com.example.streamingappzb.data.remote.tmdb.TmdbImages
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dp

/**
 * A poster (PRD §5 `Poster`).
 *
 * Loads [Title.artUrl] with Coil when there is key art. **When there is not** — which is
 * every sample title — it paints the design's drawn poster: a per-title gradient under one
 * of four light motifs, with a bottom scrim and the title bottom-start. That fallback is
 * the normal presentation and must read as finished, never as an error.
 *
 * Children added in XML or in code stack above the art, which is the design's `children`
 * slot: the play glyph on a Continue card, the NEW EPISODE strip, the episode badge.
 */
class PosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Style { Row, Hero, Tile }

    private val artView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isVisible = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val scrimView = View(context).apply {
        setBackgroundResource(R.drawable.scrim_poster_label)
        isVisible = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val labelView = TextView(context).apply {
        setTextAppearance(R.style.TextAppearance_Mh_Body_Semibold)
        setTextColor(context.color(R.color.text_hi))
        letterSpacing = -0.01f
        maxLines = 3
        isVisible = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val ringWidth = resources.getDimension(R.dimen.hairline)

    private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }

    private var c1 = context.color(R.color.night700)
    private var c2 = context.color(R.color.night900)
    private var motif = 0
    private var style = Style.Row
    private var hasArt = false
    private var showLabel = true
    private var bigLabel = false

    /** Corner radius in pixels: 6dp in rows, 10dp in grids, 0 on the hero. */
    var cornerRadius: Float = resources.getDimension(R.dimen.radius_poster_row)
        set(value) {
            field = value
            invalidateOutline()
            invalidate()
        }

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }

        addView(artView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(
            scrimView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM).apply {
                // The design's scrim covers the bottom 64% of the poster.
                height = 0
            },
        )
        addView(
            labelView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START),
        )

        context.obtainStyledAttributes(attrs, R.styleable.PosterView).use { a ->
            cornerRadius = a.getDimension(
                R.styleable.PosterView_mhCornerRadius,
                resources.getDimension(R.dimen.radius_poster_row),
            )
            showLabel = a.getBoolean(R.styleable.PosterView_mhShowLabel, true)
            bigLabel = a.getBoolean(R.styleable.PosterView_mhBigLabel, false)
            style = Style.entries[a.getInt(R.styleable.PosterView_mhPosterStyle, 0).coerceIn(0, 2)]
        }
        applyLabelMetrics()
    }

    /**
     * @param showLabel override the XML value, e.g. a backdrop that carries the title
     *   separately below it.
     */
    fun bind(title: Title, showLabel: Boolean = this.showLabel, big: Boolean = bigLabel) {
        this.showLabel = showLabel
        this.bigLabel = big
        c1 = title.c1
        c2 = title.c2
        motif = title.motif
        labelView.text = title.title
        applyLabelMetrics()

        val art = title.artUrl
        // Announce the poster once, on the container, rather than on each internal view.
        contentDescription = context.getString(R.string.cd_poster, title.title)
        if (art.isNullOrBlank()) {
            clearArt()
        } else {
            loadArt(art)
        }
        invalidate()
    }

    /**
     * A real catalogue item from TMDB or AniList.
     *
     * Almost every one of these has key art, so the drawn fallback is now the exception
     * rather than the rule — but it still has to look finished when a poster is missing or
     * fails to load. The colours are derived from the id rather than stored, so the same
     * title always draws the same way and no palette has to be shipped or fetched.
     *
     * @param backdrop use the 16:9 still instead of the 2:3 poster, for the hero.
     */
    fun bind(
        item: MediaItem,
        showLabel: Boolean = this.showLabel,
        big: Boolean = bigLabel,
        backdrop: Boolean = false,
    ) {
        this.showLabel = showLabel
        this.bigLabel = big
        val palette = MediaPalette.of(item)
        c1 = palette.c1
        c2 = palette.c2
        motif = palette.motif
        labelView.text = item.title
        applyLabelMetrics()

        val art = if (backdrop) {
            TmdbImages.backdrop(item.backdropPath) ?: TmdbImages.posterLarge(item.posterPath)
        } else {
            TmdbImages.poster(item.posterPath)
        }
        contentDescription = context.getString(R.string.cd_poster, item.title)
        if (art.isNullOrBlank()) clearArt() else loadArt(art)
        invalidate()
    }

    /** A genre browse tile (PRD §6.4), which has a colour and a motif but no title art. */
    fun bindGenre(genre: Genre) {
        style = Style.Tile
        c1 = genre.c1
        c2 = context.color(R.color.night800)
        motif = genre.motif
        showLabel = false
        clearArt()
        contentDescription = genre.name
        invalidate()
    }

    /**
     * A TMDB genre tile.
     *
     * TMDB ships no colour for a genre — it is a name and an id — so the tile's colour is
     * derived from the name, exactly as an art-less poster's is derived from its id. See
     * [MediaPalette.ofGenre] for why the *name* and not the id.
     */
    fun bindGenre(genre: MediaGenre) {
        style = Style.Tile
        val palette = MediaPalette.ofGenre(genre)
        c1 = palette.c1
        c2 = context.color(R.color.night800)
        motif = palette.motif
        showLabel = false
        clearArt()
        contentDescription = genre.name
        invalidate()
    }

    private fun loadArt(uri: String) {
        hasArt = true
        artView.isVisible = true
        // Requested at the rendered size so posters never decode larger than they draw
        // (PRD §8, Performance).
        artView.load(uri) {
            crossfade(true)
            scale(Scale.FILL)
            placeholder(null)
            error(null)
            listener(
                onError = { _, _ ->
                    // A failed load must fall back to the drawn poster, not a blank tile.
                    clearArt()
                    invalidate()
                },
            )
        }
        scrimView.isVisible = false
        labelView.isVisible = false
        requestLayout()
    }

    private fun clearArt() {
        hasArt = false
        artView.isVisible = false
        artView.setImageDrawable(null)
        // The label and its scrim exist only on the drawn poster, exactly as the design
        // gates them on `label && !t.art`.
        val show = showLabel && style != Style.Hero
        scrimView.isVisible = show
        labelView.isVisible = show
        requestLayout()
    }

    private fun applyLabelMetrics() {
        val inset = context.dp(if (bigLabel) 18 else 9)
        val bottom = context.dp(if (bigLabel) 16 else 8)
        labelView.setPadding(inset, 0, inset, bottom)
        labelView.textSize = if (bigLabel) BIG_LABEL_SP else SMALL_LABEL_SP
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        // 64% of the poster height, per the design.
        scrimView.updateLayoutParams { height = (h * SCRIM_FRACTION).toInt() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (hasArt) {
            // Coil draws over this; the surface colour shows only while decoding.
            canvas.drawColor(context.color(R.color.poster_placeholder))
            return
        }

        val colours = MotifColours.of(context)
        when (style) {
            Style.Hero -> MotifPainter.drawHeroBase(canvas, w, h, c1, c2, colours)
            Style.Tile -> {
                MotifPainter.drawTileBase(canvas, w, h, c1, c2)
                MotifPainter.drawMotif(canvas, w, h, motif, colours)
            }

            Style.Row -> {
                MotifPainter.drawPosterBase(canvas, w, h, c1, c2)
                MotifPainter.drawMotif(canvas, w, h, motif, colours)
            }
        }

        // The design's `inset 0 0 0 1px rgba(240,236,242,.06)` hairline.
        ringPaint.strokeWidth = ringWidth
        ringPaint.color = context.color(R.color.poster_inset_ring)
        val inset = ringWidth / 2f
        canvas.drawRoundRect(
            inset, inset, w - inset, h - inset,
            cornerRadius, cornerRadius, ringPaint,
        )
    }

    private inline fun <T : View> T.updateLayoutParams(block: LayoutParams.() -> Unit) {
        (layoutParams as? LayoutParams)?.let {
            it.block()
            layoutParams = it
        }
    }

    private companion object {
        const val SCRIM_FRACTION = 0.64f
        const val SMALL_LABEL_SP = 13f
        const val BIG_LABEL_SP = 22f
    }
}
