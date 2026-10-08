package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.isFinished
import com.example.streamingappzb.domain.model.isResumable
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.ProgressRepository

/**
 * Writes Continue watching, applying the PRD §6.3 end rules.
 *
 * Called every 10 s while playing, on pause, on back and on `onStop`, so it has to be
 * cheap and idempotent.
 *
 * - **Within 30 s of the end counts as finished.** A series advances to ep+1 at 0 so the
 *   viewer's card picks up the next episode; a film (or a last episode) drops out of
 *   Continue watching altogether.
 * - **Under 30 s in is not worth remembering** and clears any existing entry, matching
 *   the design's `pos > 30` guard: restarting something from the top takes it off the row.
 */
class SaveProgressUseCase(
    private val progress: ProgressRepository,
    private val catalog: CatalogRepository,
) {

    sealed interface Outcome {
        /** Stored at this position. */
        data class Saved(val progress: Progress) : Outcome

        /** Finished, and Continue watching now points at this episode from 0. */
        data class AdvancedTo(val episode: Int) : Outcome

        /** Finished with nothing to advance to — removed from Continue watching. */
        data object Completed : Outcome

        /** Too early in to be worth remembering; any existing entry was cleared. */
        data object Discarded : Outcome
    }

    suspend operator fun invoke(
        titleId: Int,
        episode: Int,
        positionSeconds: Int,
        durationSeconds: Int,
        nowMillis: Long,
    ): Outcome {
        if (durationSeconds <= 0) return Outcome.Discarded

        if (isFinished(positionSeconds, durationSeconds)) {
            val next = catalog.titleDetail(titleId)?.next(episode)
            return if (next != null) {
                // Position 0 deliberately bypasses the "under 30 s" rule: this is an
                // explicit hand-off to the next episode, not a live position.
                progress.put(Progress(titleId, next.number, 0, nowMillis))
                Outcome.AdvancedTo(next.number)
            } else {
                progress.delete(titleId)
                Outcome.Completed
            }
        }

        if (!isResumable(positionSeconds, durationSeconds)) {
            progress.delete(titleId)
            return Outcome.Discarded
        }

        val saved = Progress(titleId, episode, positionSeconds, nowMillis)
        progress.put(saved)
        return Outcome.Saved(saved)
    }
}
