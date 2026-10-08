package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.megabytesFor
import com.example.streamingappzb.domain.repository.CatalogRepository

/** One row of the download sheet: a rung and what it will cost. */
data class DownloadSizeOption(
    val rung: Rung,
    val megabytes: Double,
    val selected: Boolean,
)

/**
 * Sizes for the download sheet, shown **before anything starts** (FR-302).
 *
 * The figure is `rung.mbPerHour * runtime / 3600` — the same arithmetic
 * [com.example.streamingappzb.domain.repository.DownloadRequest] carries into the
 * Downloads list, so the estimate and the reported size cannot drift.
 */
class EstimateDownloadSizeUseCase(private val catalog: CatalogRepository) {

    suspend fun forEpisode(titleId: Int, episode: Int, rungId: String): Double {
        val duration = catalog.titleDetail(titleId)?.episode(episode)?.durationSeconds ?: return 0.0
        return catalog.rung(rungId).megabytesFor(duration)
    }

    suspend fun forSeason(titleId: Int, rungId: String): Double {
        val episodes = catalog.titleDetail(titleId)?.episodes ?: return 0.0
        val rung = catalog.rung(rungId)
        return episodes.sumOf { rung.megabytesFor(it.durationSeconds) }
    }

    /**
     * The sheet's rows.
     *
     * PRD §6.2 offers **240p, 360p and 480p** with 240p as the default. The design's
     * TitleScreen.jsx slices four rungs (240p..720p); the PRD governs behaviour, so the
     * three-rung set wins — a 720p download would also blow past the Data Saver ceiling
     * the rest of the app is built around.
     */
    suspend fun options(
        titleId: Int,
        episode: Int?,
        selectedRungId: String,
    ): List<DownloadSizeOption> {
        val rungs = catalog.rungs().filter { it.id in Rung.DOWNLOAD_RUNG_IDS }
        return rungs.map { rung ->
            val mb = if (episode == null) {
                forSeason(titleId, rung.id)
            } else {
                forEpisode(titleId, episode, rung.id)
            }
            DownloadSizeOption(rung = rung, megabytes = mb, selected = rung.id == selectedRungId)
        }
    }
}
