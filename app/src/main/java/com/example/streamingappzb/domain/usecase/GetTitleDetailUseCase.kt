package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.TitleDetail
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.repository.ProgressRepository

/**
 * Everything Title detail draws (PRD §6.2), assembled once so the fragment holds no
 * cross-repository logic.
 */
data class TitleScreenData(
    val detail: TitleDetail,
    val progress: Progress?,
    val inMyList: Boolean,
    /** Download state keyed by episode number, for the trailing DownloadGlyph. */
    val downloadsByEpisode: Map<Int, DownloadItem>,
) {
    /**
     * Episodes before the one in progress render as watched (PRD §6.2). Zero when nothing
     * has been started.
     */
    val watchedUpTo: Int get() = progress?.let { it.episode - 1 } ?: 0

    fun watchedFraction(episodeNumber: Int): Float {
        val p = progress ?: return if (episodeNumber <= watchedUpTo) 1f else 0f
        if (episodeNumber < p.episode) return 1f
        if (episodeNumber > p.episode) return 0f
        val duration = detail.episode(episodeNumber)?.durationSeconds ?: return 0f
        if (duration <= 0) return 0f
        return (p.positionSeconds.toFloat() / duration).coerceIn(0f, 1f)
    }
}

class GetTitleDetailUseCase(
    private val catalog: CatalogRepository,
    private val progress: ProgressRepository,
    private val myList: MyListRepository,
    private val downloads: DownloadRepository,
) {
    suspend operator fun invoke(titleId: Int): TitleScreenData? {
        val detail = catalog.titleDetail(titleId) ?: return null
        return TitleScreenData(
            detail = detail,
            progress = progress.get(titleId),
            inMyList = myList.contains(titleId),
            downloadsByEpisode = downloads.all()
                .filter { it.titleId == titleId }
                .associateBy { it.episode },
        )
    }
}
