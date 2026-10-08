package com.example.streamingappzb.domain.model

/**
 * Where the viewer got to. Written every 10 s while playing, on pause, on back and on
 * onStop, so it survives process death (PRD §6.3, acceptance item 12).
 */
data class Progress(
    val titleId: Int,
    val episode: Int,
    val positionSeconds: Int,
    val updatedAt: Long,
) {
    companion object {
        /**
         * Within this many seconds of the end counts as finished: the viewer moves on to
         * ep+1 at 0, or the film drops out of Continue watching (PRD §6.3).
         */
        const val FINISHED_WINDOW_SECONDS = 30

        /**
         * Below this, nothing is remembered — a few seconds of a mistaken tap should not
         * put a title in Continue watching (mirrors the design's `pos > 30` guard).
         */
        const val MIN_TRACKED_SECONDS = 30

        const val SAVE_INTERVAL_SECONDS = 10
    }
}

/**
 * Does [positionSeconds] in an episode of [durationSeconds] count as watched through?
 * Used both to advance to the next episode and to decide whether the title stays in
 * Continue watching.
 */
fun isFinished(positionSeconds: Int, durationSeconds: Int): Boolean =
    positionSeconds >= durationSeconds - Progress.FINISHED_WINDOW_SECONDS

/**
 * Should this position be kept in Continue watching? Mirrors the design's rule exactly:
 * far enough in to be worth resuming, not so far that it is effectively finished.
 */
fun isResumable(positionSeconds: Int, durationSeconds: Int): Boolean =
    positionSeconds > Progress.MIN_TRACKED_SECONDS && !isFinished(positionSeconds, durationSeconds)

/** A Continue-watching card: the progress plus everything needed to draw it. */
data class ContinueItem(
    val progress: Progress,
    val title: Title,
    val episode: Episode,
) {
    val fraction: Float
        get() = if (episode.durationSeconds <= 0) 0f
        else (progress.positionSeconds.toFloat() / episode.durationSeconds).coerceIn(0f, 1f)

    /** Rounded up so "0 min left" never shows on something still playing. */
    val minutesLeft: Int
        get() = ((episode.durationSeconds - progress.positionSeconds) + 59) / 60
}
