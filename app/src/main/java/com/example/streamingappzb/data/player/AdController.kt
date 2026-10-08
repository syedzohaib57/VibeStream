package com.example.streamingappzb.data.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.AdPlaybackState
import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.AdSlot
import com.example.streamingappzb.domain.model.AdSpot

/**
 * Pre-roll and mid-roll ads on a single ExoPlayer instance.
 *
 * ### Why not IMA
 *
 * Two acceptance items are about exact timing — "Skip unlocks at **exactly** 5 s" and
 * "mid-roll ticks line up with the authored breaks". Driving the schedule locally makes
 * both exact rather than approximate, and keeps the app free of Play Services, the
 * advertising ID, and the Data Safety disclosure that comes with an ads SDK. The schedule
 * is VMAP-shaped, so swapping in IMA later is a matter of replacing where [AdSchedule]
 * comes from.
 *
 * ### How
 *
 * One player, two roles. Starting an ad stores the content position, swaps the media item
 * to the creative and plays it; finishing restores the content item and seeks back. The
 * elapsed time comes from the player's own position, so **pausing cannot shorten the skip
 * countdown** — five seconds means five seconds of watching.
 *
 * Seeking past an unplayed break plays that break first and then lands on the seek target,
 * which is the standard behaviour the PRD asks for.
 */
@OptIn(UnstableApi::class)
class AdController(
    private val analytics: Analytics,
) {

    private var player: ExoPlayer? = null
    private var schedule: AdSchedule = AdSchedule.NONE
    private var contentItem: MediaItem? = null
    private var contentDurationSeconds: Int = 0

    private val played = mutableSetOf<String>()
    private var activeSpot: AdSpot? = null
    private var resumePositionMs: Long = 0

    /** Latest ad state, or [AdPlaybackState.IDLE] when content is playing. */
    var state: AdPlaybackState = AdPlaybackState.IDLE
        private set

    var onStateChanged: ((AdPlaybackState) -> Unit)? = null

    /** Called when an ad ends, so the caller can restore its own control state. */
    var onAdFinished: (() -> Unit)? = null

    val inAd: Boolean get() = activeSpot != null

    /** Fractions for the seek rail's ticks — the same list the breaks play from. */
    val midRollFractions: List<Float> get() = schedule.midRollFractions

    /**
     * @param startPositionSeconds where playback begins. Breaks at or before it are marked
     *   played: resuming is not the same as seeking past a break. A viewer who stopped at
     *   18:42 and comes back should not be charged for the break at 13:30 they already
     *   watched — and doing so would mean a pre-roll followed immediately by a mid-roll.
     */
    fun attach(
        player: ExoPlayer,
        schedule: AdSchedule,
        contentItem: MediaItem,
        contentDurationSeconds: Int,
        startPositionSeconds: Int,
    ) {
        this.player = player
        this.schedule = schedule
        this.contentItem = contentItem
        this.contentDurationSeconds = contentDurationSeconds
        played.clear()
        activeSpot = null

        schedule.midRollsAtOrBefore(startPositionSeconds, contentDurationSeconds)
            .forEach { played.add(it.id) }
        publish(AdPlaybackState.IDLE)
    }

    /**
     * Re-bases the breaks on the media's real duration once the player reports it.
     *
     * Breaks are authored as fractions of runtime, so the seconds they land on move when
     * the duration does. Anything now behind [currentPositionSeconds] is marked played,
     * which keeps a re-base from firing a break the viewer has already gone past.
     */
    fun updateContentDuration(durationSeconds: Int, currentPositionSeconds: Int) {
        if (durationSeconds <= 0 || durationSeconds == contentDurationSeconds) return
        contentDurationSeconds = durationSeconds
        schedule.midRollsAtOrBefore(currentPositionSeconds, durationSeconds)
            .forEach { played.add(it.id) }
    }

    /**
     * Plays the pre-roll if there is one.
     *
     * @return true when an ad took over, so the caller should not start the content yet.
     */
    fun startPreRollIfAny(contentStartPositionMs: Long): Boolean {
        val preRoll = schedule.preRoll ?: return false
        if (!played.add(preRoll.id)) return false
        resumePositionMs = contentStartPositionMs
        begin(preRoll)
        return true
    }

    /**
     * Drives the ad clock and checks for a break the content has just reached. Call on the
     * same tick that updates the position.
     */
    fun onTick() {
        val exo = player ?: return
        val spot = activeSpot

        if (spot != null) {
            val elapsed = (exo.currentPosition / MILLIS).toInt().coerceAtLeast(0)
            val reported = exo.duration.takeIf { it > 0 }?.let { (it / MILLIS).toInt() }
            // The creative's own length, but never longer than the authored cap — one
            // pre-roll, at most 30 s (PRD §6.3).
            val duration = (reported ?: spot.maxDurationSeconds)
                .coerceAtMost(spot.maxDurationSeconds)

            publish(
                AdPlaybackState(
                    spot = spot,
                    elapsedSeconds = elapsed.coerceAtMost(duration),
                    durationSeconds = duration,
                ),
            )

            if (elapsed >= duration) finish()
            return
        }

        // On content: has an unplayed mid-roll been reached?
        val positionSeconds = (exo.currentPosition / MILLIS).toInt()
        dueMidRoll(positionSeconds)?.let { due ->
            resumePositionMs = exo.currentPosition
            played.add(due.id)
            begin(due)
        }
    }

    /**
     * Intercepts a seek. If the viewer jumps past a break that has not played, the break
     * plays first and then playback lands on [targetSeconds].
     *
     * @return true when an ad intercepted, so the caller should not seek itself.
     */
    fun interceptSeek(targetSeconds: Int): Boolean {
        if (inAd) return true
        val spot = dueMidRoll(targetSeconds) ?: return false
        resumePositionMs = targetSeconds * MILLIS
        played.add(spot.id)
        begin(spot)
        return true
    }

    /** Skip, permitted only once the unlock threshold has actually been watched. */
    fun skip(): Boolean {
        val spot = activeSpot ?: return false
        if (!state.canSkip) return false
        analytics.adSkip(state.elapsedSeconds)
        finish()
        return true
    }

    /** True when the ended event belongs to an ad rather than the episode. */
    fun onPlaybackEnded(): Boolean {
        if (!inAd) return false
        finish()
        return true
    }

    /** An ad that will not load must never block the content. */
    fun onPlayerError(): Boolean {
        if (!inAd) return false
        finish()
        return true
    }

    fun release() {
        player = null
        contentItem = null
        activeSpot = null
        played.clear()
        publish(AdPlaybackState.IDLE)
    }

    // ---------------------------------------------------------------- internals

    private fun dueMidRoll(positionSeconds: Int): AdSpot? = schedule.midRolls
        .asSequence()
        .filter { it.id !in played }
        .filter { it.positionSeconds(contentDurationSeconds) in 1..positionSeconds }
        // The latest one passed, so a long jump does not queue up several breaks.
        .maxByOrNull { it.positionFraction }

    private fun begin(spot: AdSpot) {
        val exo = player ?: return
        activeSpot = spot
        analytics.adStart(spot.slot.name)
        publish(
            AdPlaybackState(
                spot = spot,
                elapsedSeconds = 0,
                durationSeconds = spot.maxDurationSeconds,
            ),
        )
        exo.setMediaItem(MediaItemFactory.forAd(spot.creative, spot.id))
        exo.prepare()
        exo.play()
    }

    private fun finish() {
        val exo = player ?: return
        val item = contentItem ?: return
        activeSpot = null
        publish(AdPlaybackState.IDLE)
        exo.setMediaItem(item)
        exo.prepare()
        exo.seekTo(resumePositionMs)
        exo.play()
        onAdFinished?.invoke()
    }

    private fun publish(next: AdPlaybackState) {
        state = next
        onStateChanged?.invoke(next)
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}

/** Pre-roll only exists for a stream; a downloaded episode was monetised already. */
internal val AdSchedule.hasPreRoll: Boolean get() = spots.any { it.slot == AdSlot.PreRoll }
