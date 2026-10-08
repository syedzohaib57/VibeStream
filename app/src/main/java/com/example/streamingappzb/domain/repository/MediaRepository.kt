package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.MovieCollection
import com.example.streamingappzb.domain.model.EpisodeSummary
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaFeed
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.model.Studio
import kotlinx.coroutines.flow.Flow

/**
 * Real catalogue metadata: TMDB for film and television, AniList for anime.
 *
 * Both are free and need no commercial agreement, and neither serves video — this
 * interface deliberately returns no stream URLs for licensed titles. Where something can
 * be watched is [com.example.streamingappzb.domain.model.WatchOptions], carried on
 * [MediaDetail].
 *
 * Every read is cache-first so the app opens instantly and still renders with no network.
 */
interface MediaRepository {

    /**
     * The discover feed for a tab.
     *
     * @param tab null is "All"; otherwise the rows are narrowed to that type.
     * @param forceRefresh ignores a fresh cache, for pull-to-refresh.
     */
    suspend fun feed(tab: MediaType?, forceRefresh: Boolean = false): MediaFeed

    /** Emits the cached feed immediately, then again whenever it is refreshed. */
    fun observeFeed(tab: MediaType?): Flow<MediaFeed>

    suspend fun detail(id: Int, type: MediaType, forceRefresh: Boolean = false): MediaDetail?

    suspend fun episodes(id: Int, type: MediaType, seasonNumber: Int): List<EpisodeSummary>

    /**
     * Search across films, series and anime at once.
     *
     * Falls back to whatever is cached when the network is unavailable, so a search made
     * offline still returns something the viewer has already seen.
     */
    suspend fun search(query: String): List<MediaItem>

    /** Titles releasing after today, soonest first (FR: "upcoming"). */
    suspend fun upcoming(type: MediaType?): List<MediaItem>

    /** Resolves ids kept in My List or Continue watching back into items. */
    suspend fun itemsByKeys(keys: List<String>): List<MediaItem>

    // ---------------------------------------------------------------- browse

    /**
     * One page of `/discover`, for the browse screens.
     *
     * Paged rather than returning everything: a genre has thousands of titles and the grid
     * loads them as the viewer scrolls. Not cached in Room — a browse result is a view over
     * the catalogue rather than part of it, and the HTTP cache already makes a second visit
     * free.
     */
    suspend fun browse(query: BrowseQuery): List<MediaItem>

    /**
     * Everything in a film series, e.g. all of Middle-earth from any one of them.
     *
     * Null when the id is unknown or the series has only the one film — see
     * [MovieCollection.isWorthShowing] for why a collection of one is not a row.
     */
    suspend fun collection(id: Int): MovieCollection?

    /**
     * A person and their filmography.
     *
     * One call: the credits come back appended to the detail, which is the difference
     * between three round trips and one on a screen that is reached by tapping a face.
     */
    suspend fun person(id: Int): PersonProfile?

    /** A studio or broadcaster, for the "more from" heading. */
    suspend fun studio(id: Int, isNetwork: Boolean): Studio?

    /**
     * A single episode's full detail.
     *
     * The season listing omits `runtime` for a fair number of series, and this is the only
     * endpoint that reports it — so it is fetched per episode, lazily, as rows are bound.
     */
    suspend fun episode(id: Int, seasonNumber: Int, episodeNumber: Int): EpisodeSummary?
}

/**
 * The region used for watch-provider lookups, e.g. "PK".
 *
 * Provider availability is per-country and the difference is not cosmetic — a title on
 * Netflix in the US may be nowhere in Pakistan — so this is read from the device rather
 * than hardcoded, and is overridable by the viewer.
 */
interface RegionRepository {
    /** ISO 3166-1 alpha-2, upper case. */
    fun region(): String

    fun setRegion(code: String)

    /** The device's own country, used until the viewer chooses otherwise. */
    fun deviceRegion(): String
}

/**
 * My List for real catalogue items, keyed by `type:id` rather than a bare integer —
 * see [com.example.streamingappzb.data.db.MediaListEntity] for why that matters.
 */
interface MediaListRepository {

    /** Most recently added first, which is the grid's order. */
    fun observeKeys(): Flow<List<String>>

    suspend fun keys(): List<String>

    suspend fun contains(item: MediaItem): Boolean

    /** Returns the state after toggling, so callers can pick the right toast. */
    suspend fun toggle(item: MediaItem): Boolean
}
