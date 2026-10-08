package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.ColorInt
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The fixed light colours the motifs are built from.
 *
 * Read from `colors.xml` rather than hardcoded here, so the palette stays the single
 * source of truth and TradeDressTest's sweep covers these too. Cached because they are
 * process-wide constants and [MotifPainter] is called from `onDraw`.
 */
data class MotifColours(
    @ColorInt val emberKey: Int,
    @ColorInt val emberSoft: Int,
    @ColorInt val emberMid: Int,
    @ColorInt val cyanRim: Int,
    @ColorInt val sheen: Int,
    @ColorInt val spotlight: Int,
    @ColorInt val rosetteCore: Int,
    @ColorInt val rosetteInner: Int,
    @ColorInt val rosetteOuter: Int,
) {
    companion object {
        @Volatile
        private var cached: MotifColours? = null

        fun of(context: Context): MotifColours = cached ?: MotifColours(
            emberKey = context.color(R.color.motif_ember_key),
            emberSoft = context.color(R.color.motif_ember_soft),
            emberMid = context.color(R.color.motif_ember_mid),
            cyanRim = context.color(R.color.motif_cyan_rim),
            sheen = context.color(R.color.motif_sheen),
            spotlight = context.color(R.color.motif_spotlight),
            rosetteCore = context.color(R.color.rosette_core),
            rosetteInner = context.color(R.color.rosette_inner),
            rosetteOuter = context.color(R.color.rosette_outer),
        ).also { cached = it }
    }
}

/**
 * Paints the design's poster art.
 *
 * Every sample title ships without key art, so the drawn poster is the normal case, not a
 * placeholder — it has to look finished (PRD §5). It is a per-title linear gradient from
 * `c1` to `c2` under one of four "cinematic light" motifs (Shared.jsx `MOTIFS`): ember key
 * light, cyan rim, a sheen, a spotlight.
 *
 * Drawn rather than shipped as bitmaps because the base gradient is **per title** — a
 * raster could not be recoloured for fourteen different `c1`/`c2` pairs, and a vector
 * drawable cannot express a CSS gradient's arbitrary angle plus elliptical radial stops in
 * one pass. The fixed-colour motif layers are composited over the tinted base, exactly how
 * the CSS stacks them.
 *
 * ### CSS angles
 *
 * `linear-gradient(Ndeg, ...)` measures N clockwise from "to top", so the direction vector
 * in screen coordinates (y down) is `(sin N, cos N)` and the gradient line spans
 * `|w·sin N| + |h·cos N|` through the centre. [drawLinear] implements that, so the Kotlin
 * matches the CSS one for one.
 */
object MotifPainter {

    /** The four motifs from Shared.jsx, in order. */
    const val MOTIF_COUNT = 4

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Base poster gradient: `linear-gradient(160deg, c1 0%, c2 100%)`. */
    fun drawPosterBase(canvas: Canvas, w: Float, h: Float, @ColorInt c1: Int, @ColorInt c2: Int) {
        drawLinear(canvas, w, h, 160f, intArrayOf(c1, c2), floatArrayOf(0f, 1f))
    }

    /**
     * Hero background, which HomeScreen.jsx overrides wholesale:
     * ```
     * radial-gradient(90% 55% at 78% 10%, rgba(255,177,92,.6), transparent 62%),
     * radial-gradient(70% 45% at 8% 70%, rgba(92,225,230,.18), transparent 65%),
     * linear-gradient(172deg, c1, c2 88%)
     * ```
     */
    fun drawHeroBase(
        canvas: Canvas,
        w: Float,
        h: Float,
        @ColorInt c1: Int,
        @ColorInt c2: Int,
        colours: MotifColours,
    ) {
        drawLinear(canvas, w, h, 172f, intArrayOf(c1, c2), floatArrayOf(0f, 0.88f))
        drawRadial(canvas, w, h, 0.78f, 0.10f, 0.90f, 0.55f, withAlpha(colours.emberKey, HERO_EMBER_ALPHA), 0.62f)
        drawRadial(canvas, w, h, 0.08f, 0.70f, 0.70f, 0.45f, withAlpha(colours.cyanRim, HERO_CYAN_ALPHA), 0.65f)
    }

    /**
     * Genre browse tile (LibraryScreens.jsx):
     * `MOTIFS[g.motif] + ', linear-gradient(140deg, g.c1, var(--mh-night-800))'`
     */
    fun drawTileBase(canvas: Canvas, w: Float, h: Float, @ColorInt c1: Int, @ColorInt night800: Int) {
        drawLinear(canvas, w, h, 140f, intArrayOf(c1, night800), floatArrayOf(0f, 1f))
    }

    /** One of the four light motifs, composited over whatever base was drawn. */
    fun drawMotif(canvas: Canvas, w: Float, h: Float, motif: Int, colours: MotifColours) {
        when (motif % MOTIF_COUNT) {
            // radial-gradient(120% 70% at 85% -5%, rgba(255,177,92,.5), transparent 60%)
            0 -> drawRadial(canvas, w, h, 0.85f, -0.05f, 1.20f, 0.70f, colours.emberKey, 0.60f)

            // radial-gradient(90% 60% at 10% 105%, rgba(92,225,230,.3), transparent 65%),
            // radial-gradient(80% 50% at 90% 0%, rgba(255,138,43,.22), transparent 60%)
            1 -> {
                drawRadial(canvas, w, h, 0.10f, 1.05f, 0.90f, 0.60f, colours.cyanRim, 0.65f)
                drawRadial(canvas, w, h, 0.90f, 0f, 0.80f, 0.50f, colours.emberSoft, 0.60f)
            }

            // linear-gradient(115deg, transparent 38%, rgba(255,255,255,.09) 50%, transparent 62%),
            // radial-gradient(70% 50% at 50% 0%, rgba(255,177,92,.3), transparent 70%)
            2 -> {
                drawLinear(
                    canvas, w, h, 115f,
                    intArrayOf(TRANSPARENT, TRANSPARENT, colours.sheen, TRANSPARENT, TRANSPARENT),
                    floatArrayOf(0f, 0.38f, 0.50f, 0.62f, 1f),
                )
                drawRadial(canvas, w, h, 0.50f, 0f, 0.70f, 0.50f, colours.emberMid, 0.70f)
            }

            // radial-gradient(55% 42% at 50% 36%, rgba(255,236,210,.22), transparent 72%)
            else -> drawRadial(canvas, w, h, 0.50f, 0.36f, 0.55f, 0.42f, colours.spotlight, 0.72f)
        }
    }

    /**
     * The projector halo (Shared.jsx `Rosette`), drawn at an explicit centre and diameter
     * so it can overhang the view it sits in, as it does on the hero.
     */
    fun drawRosette(canvas: Canvas, cx: Float, cy: Float, diameter: Float, colours: MotifColours) {
        if (diameter <= 0f) return
        val r = diameter / 2f
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                colours.rosetteCore,
                colours.rosetteCore,
                colours.rosetteInner,
                colours.rosetteOuter,
                TRANSPARENT,
            ),
            floatArrayOf(0f, 0.06f, 0.22f, 0.48f, 0.70f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    // ---------- CSS gradient primitives ----------

    private fun drawLinear(
        canvas: Canvas,
        w: Float,
        h: Float,
        cssAngleDegrees: Float,
        colors: IntArray,
        stops: FloatArray,
    ) {
        if (w <= 0f || h <= 0f) return
        val radians = Math.toRadians(cssAngleDegrees.toDouble())
        val dx = sin(radians).toFloat()
        val dy = cos(radians).toFloat()
        val length = abs(w * dx) + abs(h * dy)
        val cx = w / 2f
        val cy = h / 2f
        val half = length / 2f
        paint.shader = LinearGradient(
            cx - dx * half, cy - dy * half,
            cx + dx * half, cy + dy * half,
            colors, stops, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    /**
     * A CSS elliptical radial gradient. Android radial gradients are circular, so the
     * ellipse comes from a local matrix that scales y by `ry / rx` about the centre.
     */
    private fun drawRadial(
        canvas: Canvas,
        w: Float,
        h: Float,
        centreXFraction: Float,
        centreYFraction: Float,
        radiusXFraction: Float,
        radiusYFraction: Float,
        @ColorInt colour: Int,
        endStop: Float,
    ) {
        if (w <= 0f || h <= 0f) return
        val cx = centreXFraction * w
        val cy = centreYFraction * h
        val rx = radiusXFraction * w
        val ry = radiusYFraction * h
        if (rx <= 0f || ry <= 0f) return

        paint.shader = RadialGradient(
            cx, cy, rx,
            intArrayOf(colour, colour and RGB_MASK),
            floatArrayOf(0f, endStop.coerceIn(0.01f, 1f)),
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(Matrix().apply { postScale(1f, ry / rx, cx, cy) })
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    /** Keeps a token's hue while the hero's own stops set a different opacity. */
    @ColorInt
    private fun withAlpha(@ColorInt colour: Int, alpha: Int): Int =
        (colour and RGB_MASK) or (alpha shl 24)

    private const val TRANSPARENT = 0x00000000
    private const val RGB_MASK = 0x00FFFFFF

    /** The hero's own washes are stronger than the poster motifs': .6 and .18. */
    private const val HERO_EMBER_ALPHA = 0x99
    private const val HERO_CYAN_ALPHA = 0x2E
}
