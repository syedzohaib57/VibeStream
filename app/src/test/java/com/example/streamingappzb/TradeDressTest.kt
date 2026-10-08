package com.example.streamingappzb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Acceptance item 1: **no colour within CIEDE2000 ΔE 10 of `#E50914` appears anywhere, and
 * the wordmark is ember.**
 *
 * A build-time guard rather than a review checklist. It is not hypothetical: VibeStream is
 * a streaming app in a true-black theatre palette, and reaching for a "warning red" is
 * exactly the mistake the trade-dress rule (PRD §2.2 / §8.5) exists to prevent.
 *
 * CIEDE2000 rather than a plain RGB distance because perceptual difference is what the
 * rule is about — two colours can be far apart in RGB and still read as the same red.
 */
class TradeDressTest {

    /** The colour the brand must stay clear of. */
    private val forbidden = intArrayOf(0xE5, 0x09, 0x14)

    private val minimumDeltaE = 10.0

    private fun moduleFile(relative: String): File =
        listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.exists() }
            ?: error("Not found from ${File(".").absolutePath}: $relative")

    private data class NamedColour(val name: String, val argb: Long) {
        val rgb: IntArray
            get() = intArrayOf(
                ((argb shr 16) and 0xFF).toInt(),
                ((argb shr 8) and 0xFF).toInt(),
                (argb and 0xFF).toInt(),
            )

        val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    }

    private fun readColours(): List<NamedColour> {
        val xml = moduleFile("src/main/res/values/colors.xml").readText()
        val pattern = Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6,8})\s*</color>""")
        return pattern.findAll(xml).map { match ->
            val (name, hex) = match.destructured
            val argb = if (hex.length == 6) 0xFF000000L or hex.toLong(16) else hex.toLong(16)
            NamedColour(name, argb)
        }.toList()
    }

    @Test
    fun `no palette colour sits within delta E 10 of the forbidden red`() {
        val colours = readColours()
        assertTrue("colors.xml produced no colours to check", colours.size > 20)

        val offenders = colours
            // Near-transparent overlays cannot read as a brand colour at all.
            .filter { it.alpha > 0x40 }
            .map { it to deltaE2000(it.rgb, forbidden) }
            .filter { (_, delta) -> delta < minimumDeltaE }

        assertTrue(
            "these colours are within CIEDE2000 dE $minimumDeltaE of #E50914: " +
                offenders.joinToString { (colour, delta) ->
                    "${colour.name} (dE ${"%.1f".format(delta)})"
                },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the accent roles are the ones the PRD fixes`() {
        val byName = readColours().associateBy { it.name }

        // ember = play and progress, rose = new and ads, cyan = offline and saving data.
        assertEquals(0xFFFF8A2BL, byName.getValue("ember500").argb)
        assertEquals(0xFFF0436AL, byName.getValue("rose500").argb)
        assertEquals(0xFF22C3CCL, byName.getValue("cyan500").argb)
        // True black, not near-black.
        assertEquals(0xFF050505L, byName.getValue("night900").argb)
        // Text on ember, which is what gives the 9.4:1 contrast the PRD claims.
        assertEquals(0xFF1A0A00L, byName.getValue("text_on_ember").argb)
    }

    @Test
    fun `the rose accent is deliberately far from the forbidden red`() {
        // rose500 is the nearest thing in the palette to a red, so it is the one worth
        // stating a margin for. Measured at dE 17.7 — a 77% margin over the dE 10 rule.
        val rose = readColours().single { it.name == "rose500" }
        val delta = deltaE2000(rose.rgb, forbidden)
        assertTrue(
            "rose500 must stay clear of #E50914, was dE ${"%.1f".format(delta)}",
            delta > 15.0,
        )
    }

    /**
     * The casing assertion that used to be here is deliberately gone.
     *
     * It pinned the wordmark to all-lowercase, which was a property of the old `moviehub`
     * brand rather than a trade-dress rule — the rule the file exists to enforce is about
     * *colour*, and that is tested above and is unaffected by a rename. Keeping a casing
     * check after the brand became `VibeStream` would have meant a red build asserting a
     * rule the product no longer has.
     *
     * What is still worth pinning: the wordmark matches the brand, and it is never
     * translated. A localised wordmark is a different mark.
     */
    @Test
    fun `the wordmark is the brand and is never translated`() {
        val strings = moduleFile("src/main/res/values/strings.xml").readText()
        val match = Regex("""<string\s+name="wordmark"[^>]*>([^<]+)</string>""").find(strings)
        assertNotNull("strings.xml has no wordmark", match)
        val wordmark = match!!.groupValues[1]
        assertEquals("VibeStream", wordmark)
        assertTrue(
            "the wordmark must not be translatable",
            Regex("""<string\s+name="wordmark"[^>]*translatable="false"""").containsMatchIn(strings),
        )
    }

    /** The launcher label and the wordmark are one brand; drifting apart is the bug. */
    @Test
    fun `the app name matches the wordmark`() {
        val strings = moduleFile("src/main/res/values/strings.xml").readText()
        val appName = Regex("""<string\s+name="app_name"[^>]*>([^<]+)</string>""")
            .find(strings)
            ?.groupValues
            ?.get(1)
        assertEquals("VibeStream", appName)
    }

    /**
     * PRD §8 quotes 21:1, 9.6:1 and 9.4:1. Measured against the actual tokens with the
     * WCAG formula:
     *
     * | pair                            | PRD    | measured |
     * |---------------------------------|--------|----------|
     * | text_hi on night900             | 21:1   | 20.4:1   |
     * | text_mid on night900            | 9.6:1  | 9.8:1    |
     * | text_on_ember on ember500       | 9.4:1  | **8.2:1**|
     *
     * The first two are the PRD's figures within rounding. The third is not: `#1A0A00` on
     * `#FF8A2B` is 8.2:1, not 9.4:1. It still clears WCAG **AAA** for normal text (7:1),
     * so the tokens stand as designed — but the assertions below are the measured truth
     * rather than the quoted numbers, so this test cannot pass on a false premise.
     */
    @Test
    fun `contrast on the true-black background clears WCAG AAA`() {
        val byName = readColours().associateBy { it.name }
        val background = byName.getValue("night900").rgb

        val textHi = contrastRatio(byName.getValue("text_hi").rgb, background)
        val textMid = contrastRatio(byName.getValue("text_mid").rgb, background)
        val onEmber = contrastRatio(
            byName.getValue("text_on_ember").rgb,
            byName.getValue("ember500").rgb,
        )

        assertTrue("text_hi on night900 was ${"%.1f".format(textHi)}:1", textHi > 20.0)
        assertTrue("text_mid on night900 was ${"%.1f".format(textMid)}:1", textMid > 9.0)
        // AAA for normal text is 7:1; ember buttons carry body-size labels.
        assertTrue(
            "text_on_ember on ember500 was ${"%.1f".format(onEmber)}:1, below WCAG AAA",
            onEmber > 7.0,
        )
    }

    // ------------------------------------------------------------ colour science

    /** WCAG relative luminance. */
    private fun luminance(rgb: IntArray): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
    }

    private fun contrastRatio(a: IntArray, b: IntArray): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** sRGB to CIELAB, D65. */
    private fun toLab(rgb: IntArray): DoubleArray {
        fun linear(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }

        val r = linear(rgb[0])
        val g = linear(rgb[1])
        val b = linear(rgb[2])

        // sRGB -> XYZ, then normalised against the D65 white point.
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        fun f(t: Double): Double =
            if (t > 216.0 / 24389.0) cbrt(t) else (841.0 / 108.0) * t + 4.0 / 29.0

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** CIEDE2000 colour difference. */
    private fun deltaE2000(rgb1: IntArray, rgb2: IntArray): Double {
        val (l1, a1, b1) = toLab(rgb1)
        val (l2, a2, b2) = toLab(rgb2)

        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cBar = (c1 + c2) / 2.0

        val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
        val a1p = a1 * (1 + g)
        val a2p = a2 * (1 + g)
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)

        fun hueDegrees(a: Double, b: Double): Double {
            if (a == 0.0 && b == 0.0) return 0.0
            val h = Math.toDegrees(atan2(b, a))
            return if (h >= 0) h else h + 360
        }

        val h1p = hueDegrees(a1p, b1)
        val h2p = hueDegrees(a2p, b2)

        val dLp = l2 - l1
        val dCp = c2p - c1p

        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2))

        val lBarP = (l1 + l2) / 2
        val cBarP = (c1p + c2p) / 2

        val hBarP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }

        val t = 1 -
            0.17 * cos(Math.toRadians(hBarP - 30)) +
            0.24 * cos(Math.toRadians(2 * hBarP)) +
            0.32 * cos(Math.toRadians(3 * hBarP + 6)) -
            0.20 * cos(Math.toRadians(4 * hBarP - 63))

        val dTheta = 30 * exp(-((hBarP - 275) / 25).pow(2))
        val rc = 2 * sqrt(cBarP.pow(7) / (cBarP.pow(7) + 25.0.pow(7)))
        val sl = 1 + (0.015 * (lBarP - 50).pow(2)) / sqrt(20 + (lBarP - 50).pow(2))
        val sc = 1 + 0.045 * cBarP
        val sh = 1 + 0.015 * cBarP * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc

        return sqrt(
            (dLp / sl).pow(2) +
                (dCp / sc).pow(2) +
                (dHp / sh).pow(2) +
                rt * (dCp / sc) * (dHp / sh),
        )
    }

    private operator fun DoubleArray.component1() = this[0]
    private operator fun DoubleArray.component2() = this[1]
    private operator fun DoubleArray.component3() = this[2]
}
