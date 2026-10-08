package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.megabytesFor
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.DownloadRequest
import com.example.streamingappzb.domain.repository.NetworkRepository
import com.example.streamingappzb.domain.repository.SettingsRepository

/**
 * The only way a download is started (FR-301..303).
 *
 * Two rules are enforced here so no screen can bypass them:
 *
 * - **A stream-only title never enqueues.** The rights record is checked first and the
 *   caller gets [Result.StreamOnly] to toast (PRD §6.2, acceptance item 11).
 * - **On mobile data with Wi-Fi-only on, the item enters `waiting`** rather than
 *   starting, unless the viewer explicitly chose "Use mobile data now" (FR-303).
 */
class EnqueueDownloadUseCase(
    private val catalog: CatalogRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val network: NetworkRepository,
) {

    sealed interface Result {
        /** The rights record forbids it. Show the licence toast; nothing was enqueued. */
        data object StreamOnly : Result

        /**
         * Accepted. [waitingForWifi] means it is parked until the connection is unmetered.
         * [episodeCount] and [firstEpisode] pick the right toast wording.
         */
        data class Enqueued(
            val episodeCount: Int,
            val firstEpisode: Int,
            val waitingForWifi: Boolean,
        ) : Result

        /** Nothing left to fetch — every episode asked for is already present. */
        data object AlreadyPresent : Result
    }

    suspend operator fun invoke(
        titleId: Int,
        episodes: List<Int>,
        rungId: String,
        useMobileDataNow: Boolean = false,
    ): Result {
        val detail = catalog.titleDetail(titleId) ?: return Result.AlreadyPresent
        if (!detail.title.downloadable) return Result.StreamOnly
        if (episodes.isEmpty()) return Result.AlreadyPresent

        val metered = network.state.value.isMetered
        val waiting = metered && settings.wifiOnly.value && !useMobileDataNow
        // Consent only means anything on a metered connection: enqueuing on Wi-Fi must not
        // quietly grant permission to keep going once the phone moves onto mobile data.
        val allowMetered = metered && useMobileDataNow

        val rung = catalog.rung(rungId)
        val requests = episodes
            .mapNotNull { detail.episode(it) }
            // Asking again for something already present must not restart it, matching
            // the design's `if (!n[k])` guard.
            .filterNot { downloads.get(titleId, it.number) != null }
            .map { episode ->
                DownloadRequest(
                    titleId = titleId,
                    episode = episode.number,
                    rungId = rung.id,
                    megabytes = rung.megabytesFor(episode.durationSeconds),
                    queuedForWifi = waiting,
                    allowMetered = allowMetered,
                )
            }

        if (requests.isEmpty()) return Result.AlreadyPresent

        downloads.enqueue(requests)
        return Result.Enqueued(
            episodeCount = requests.size,
            firstEpisode = requests.first().episode,
            waitingForWifi = waiting,
        )
    }
}
