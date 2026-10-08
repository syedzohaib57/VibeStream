package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.GenreScope
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.repository.GenreRepository
import com.example.streamingappzb.domain.repository.MediaRepository

/**
 * The browse screen: a genre grid, and a page of titles once one is picked.
 *
 * A use case rather than two repository calls from the ViewModel because the two are
 * coupled — a grid of genre tiles is useless without the names, and the names come from a
 * different repository with a different cache lifetime than the titles do.
 */
class GetBrowseResultsUseCase(
    private val media: MediaRepository,
    private val genres: GenreRepository,
) {

    /** The tiles, alphabetical. Falls back to the built-in list when TMDB is unreachable. */
    suspend fun genres(scope: GenreScope): List<MediaGenre> = genres.genres(scope)

    /**
     * One page of results.
     *
     * Returns an empty list rather than throwing: a browse grid that has loaded three pages
     * and fails on the fourth should stop growing, not lose what the viewer is reading.
     */
    suspend operator fun invoke(query: BrowseQuery): List<MediaItem> = media.browse(query)

    /**
     * Whether another page is worth asking for.
     *
     * TMDB caps `/discover` at 500 pages and answers 400 past that, so the ceiling is
     * checked here rather than discovered as an error at the bottom of a scroll.
     */
    fun hasMore(query: BrowseQuery, lastPageSize: Int): Boolean =
        lastPageSize >= MIN_FULL_PAGE && query.page < MAX_PAGE

    private companion object {
        /** A short page means the end of the result set, not a transient failure. */
        const val MIN_FULL_PAGE = 20

        /** TMDB's own `/discover` limit. */
        const val MAX_PAGE = 500
    }
}
