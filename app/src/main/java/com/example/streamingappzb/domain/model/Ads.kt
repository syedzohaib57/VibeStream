package com.example.streamingappzb.domain.model

import kotlin.math.roundToInt

/** Where an ad sits relative to the content. */
enum class AdSlot { PreRoll, MidRoll }

/** A single creative to play. */
data class AdCreative(
    val url: String,
    val type: StreamType,
)

/**
 * One authored ad opportunity.
 *
 * @param positionFraction 0 for a pre-roll; for a mid-roll, the fraction of content
 *   runtime the break sits at. The seek rail's rose ticks and the controller read this
 *   same value, which is what makes the ticks line up with the breaks exactly
 *   (acceptance item 8).
 * @param skipAfterSeconds seconds of *ad playback* before Skip unlocks — 5 by rule, and
 *   measured against playback rather than wall clock so pausing cannot shorten it.
 */
data class AdSpot(
    val id: String,
    val slot: AdSlot,
    val positionFraction: Float,
    val creative: AdCreative,
    val skipAfterSeconds: Int,
    val maxDurationSeconds: Int,
) {
    fun positionSeconds(contentDurationSeconds: Int): Int =
        (positionFraction * contentDurationSeconds).roundToInt()
}

/**
 * The ad plan for one playback session.
 *
 * A downloaded episode gets [NONE]: it was monetised at download time, so it plays
 * ad-free (FR-505, and the design's `ad: dl ? 0 : 15`).
 */
data class AdSchedule(
    val spots: List<AdSpot>,
) {
    val preRoll: AdSpot? get() = spots.firstOrNull { it.slot == AdSlot.PreRoll }

    val midRolls: List<AdSpot> get() = spots.filter { it.slot == AdSlot.MidRoll }

    /** Fractions for the seek rail's ticks, in order. */
    val midRollFractions: List<Float> get() = midRolls.map { it.positionFraction }.sorted()

    val isEmpty: Boolean get() = spots.isEmpty()

    /**
     * The breaks that sit at or before [positionSeconds].
     *
     * Used when playback *starts* at a resume point: those breaks are marked played rather
     * than fired. Resuming is not the same as seeking past a break — someone who stopped at
     * 18:42 already watched the break at 13:30, and firing it would mean a pre-roll
     * followed immediately by a mid-roll.
     */
    fun midRollsAtOrBefore(positionSeconds: Int, contentDurationSeconds: Int): List<AdSpot> =
        if (positionSeconds <= 0) {
            emptyList()
        } else {
            midRolls.filter { it.positionSeconds(contentDurationSeconds) <= positionSeconds }
        }

    companion object {
        val NONE = AdSchedule(emptyList())

        /** One pre-roll, at most 30 s, skippable after 5 (PRD §6.3). */
        const val MAX_PRE_ROLL_SECONDS = 30
        const val DEFAULT_SKIP_AFTER_SECONDS = 5
    }
}

/**
 * Live ad playback state, surfaced to the player UI.
 *
 * While [inAd] is true every other control sits at 45% opacity and does nothing
 * (PRD §6.3).
 */
data class AdPlaybackState(
    val spot: AdSpot? = null,
    val elapsedSeconds: Int = 0,
    val durationSeconds: Int = 0,
) {
    val inAd: Boolean get() = spot != null

    /** Skip unlocks at exactly the configured second of ad playback. */
    val canSkip: Boolean get() = spot != null && elapsedSeconds >= spot.skipAfterSeconds

    /** Countdown shown on the disabled Skip button: 5, 4, 3, 2, 1. */
    val secondsUntilSkip: Int
        get() = spot?.let { (it.skipAfterSeconds - elapsedSeconds).coerceAtLeast(0) } ?: 0

    val remainingSeconds: Int get() = (durationSeconds - elapsedSeconds).coerceAtLeast(0)

    val progressFraction: Float
        get() = if (durationSeconds <= 0) 0f else (elapsedSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)

    companion object {
        val IDLE = AdPlaybackState()

        /** Controls are dimmed to this while an ad plays. */
        const val DIMMED_ALPHA = 0.45f
    }
}
