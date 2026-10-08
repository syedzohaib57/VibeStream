package com.example.streamingappzb.domain.format

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Time and size formatting, kept at parity with `fmt` and `fmtMB` in the design's
 * Shared.jsx so the player's time codes and every MB figure read exactly as designed.
 *
 * Callers render these with a tabular-figure text appearance (PRD §2.2) so the digits
 * do not jitter as they tick.
 */
object Format {

    /**
     * Seconds as a time code: `0:07`, `9:05`, `1:52:30`. The hour part is omitted under
     * an hour, and minutes are unpadded in that case — `9:05`, not `09:05`.
     */
    fun time(seconds: Number): String {
        val s = seconds.toDouble().roundToLong().coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val head = if (h > 0) "$h:${pad(m)}" else "$m"
        return "$head:${pad(sec)}"
    }

    /**
     * Megabytes, switching to GB at 1000 — `155 MB`, `1.05 GB`. MB is 10^6 bytes here,
     * the unit carriers bill in, which is what makes the §6.7 ladder's figures line up.
     */
    fun megabytes(mb: Double): String =
        if (mb >= GB_THRESHOLD) {
            String.format(Locale.getDefault(), "%.2f GB", mb / GB_THRESHOLD)
        } else {
            String.format(Locale.getDefault(), "%d MB", mb.roundToInt())
        }

    fun megabytes(mb: Int): String = megabytes(mb.toDouble())

    /** Runtime split for the hero's `1h 52m` (PRD §6.1). */
    fun runtimeParts(minutes: Int): Pair<Int, Int> = minutes / 60 to minutes % 60

    /** Minutes, rounded up, so a part-minute never reads as "0 min". */
    fun minutesFromSeconds(seconds: Int): Int = (seconds + 59) / 60

    /** Whole percent, clamped — download rows never show 101%. */
    fun percent(fraction: Float): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

    private fun pad(v: Long): String = if (v < 10) "0$v" else "$v"

    private const val GB_THRESHOLD = 1000.0
}
