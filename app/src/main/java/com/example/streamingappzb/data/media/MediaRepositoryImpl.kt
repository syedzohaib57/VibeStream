package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.db.MediaDao
import com.example.streamingappzb.data.remote.anilist.AniListApi
import com.example.streamingappzb.data.remote.anilist.AniListMappers
import com.example.streamingappzb.data.remote.anilist.AniListQueries
import com.example.streamingappzb.data.remote.anilist.AniListRequest
import com.example.streamingappzb.data.remote.tmdb.GenreNamer
import com.example.streamingappzb.data.remote.tmdb.TmdbApi
import com.example.streamingappzb.data.remote.tmdb.TmdbGenres
import com.example.streamingappzb.data.remote.tmdb.TmdbMappers
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSummaryDto
import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.BrowseSort
import com.example.streamingappzb.domain.model.MovieCollection
import com.example.streamingappzb.domain.model.EpisodeSummary
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaFeed
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaRow
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.model.Studio
import com.example.streamingappzb.domain.model.WatchOptions
import com.example.streamingappzb.domain.repository.GenreRepository
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.repository.RegionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The real catalogue: TMDB for film and television, AniList for anime, Room as the cache.
 *
 * ### Cache-first, always
 *
 * Every read serves the cache immediately and refreshes behind it. A cold open with no
 * network still draws a full Home from the last session, which is the difference between
 * an app that works on a bad connection and one that shows a spinner.
 *
 * ### Failures are partial, never total
 *
 * Rows are fetched concurrently and each one is allowed to fail on its own. One endpoint
 * being down costs that row, not the screen — and if every row fails, the cache is still
 * what gets returned.
 */
class MediaRepositoryImpl(
    private val tmdb: TmdbApi,
    private val aniList: AniListApi,
    private val dao: MediaDao,
    private val region: RegionRepository,
    private val genres: GenreRepository,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : MediaRepository {

    /** Bumped after every successful refresh so observers re-read the cache. */
    private val revision = MutableStateFlow(0L)

    /**
     * TMDB's own genre names rather than the built-in map, so a poster subtitle localises
     * with the rest of the response and a newly added genre is not blank. Reads an
     * in-memory map, so passing it per item costs nothing.
     */
    private val genreNames: GenreNamer = { ids -> genres.names(ids) }

    // ------------------------------------------------------------------ feed

    override suspend fun feed(tab: MediaType?, forceRefresh: Boolean): MediaFeed {
        // Names are needed to draw a row, so the vocabulary is loaded before the rows are
        // mapped rather than after — otherwise the first paint has no genre subtitles.
        runCatching { genres.ensureLoaded() }

        if (forceRefresh || isStale(tab)) {
            runCatching { refresh(tab) }
        }
        return cachedFeed(tab)
    }

    override fun observeFeed(tab: MediaType?): Flow<MediaFeed> =
        revision.map { cachedFeed(tab) }

    private suspend fun isStale(tab: MediaType?): Boolean = withContext(io) {
        val oldest = dao.oldestRowTimestamp(MediaMapping.tabPrefix(tab)) ?: return@withContext true
        clock.nowMillis() - oldest > CACHE_TTL_MILLIS
    }

    private suspend fun cachedFeed(tab: MediaType?): MediaFeed = withContext(io) {
        val prefix = MediaMapping.tabPrefix(tab)
        val rows = dao.rowsForTab(prefix).map { cached ->
            MediaRow(
                key = cached.rowKey.removePrefix(prefix),
                title = cached.rowTitle,
                items = MediaMapping.toItems(dao.itemsForRow(cached.rowKey)),
            )
        }.filterNot { it.isEmpty }.take(MediaFeed.MAX_ROWS)

        // The hero is the strongest item of the first row rather than an editorial pick:
        // there is no editor, and the top of what is genuinely trending is always present.
        val hero = rows.firstOrNull()?.items?.firstOrNull { it.backdropPath != null }
            ?: rows.firstOrNull()?.items?.firstOrNull()

        MediaFeed(hero = hero, rows = rows)
    }

    /** Fetches every row for a tab concurrently, writing each one as it lands. */
    private suspend fun refresh(tab: MediaType?) = coroutineScope {
        val specs = RowSpecs.forTab(tab)
        specs.mapIndexed { order, spec ->
            async {
                runCatching { fetch(spec) }
                    .onSuccess { items ->
                        if (items.isNotEmpty()) writeRow(tab, spec, order, items)
                    }
            }
        }.forEach { it.await() }
        revision.value = clock.nowMillis()
    }

    private suspend fun writeRow(
        tab: MediaType?,
        spec: RowSpec,
        order: Int,
        items: List<MediaItem>,
    ) = withContext(io) {
        val now = clock.nowMillis()
        val key = MediaMapping.rowKey(tab, spec.key)
        val trimmed = items.take(MAX_ROW_ITEMS)
        dao.replaceRow(
            rowKey = key,
            items = trimmed.map { MediaMapping.toEntity(it, now) },
            members = MediaMapping.members(key, spec.title, order, trimmed, now),
        )
    }

    private suspend fun fetch(spec: RowSpec): List<MediaItem> = when (spec.source) {
        RowSource.Trending -> items(tmdb.trending().results, null)
        RowSource.TrendingToday -> items(tmdb.trendingToday().results, null)
        RowSource.PopularMovies -> items(tmdb.popularMovies().results, MediaType.Movie)
        RowSource.PopularTv -> items(tmdb.popularTv().results, MediaType.Tv)
        RowSource.TopRatedMovies -> items(tmdb.topRatedMovies().results, MediaType.Movie)
        RowSource.TopRatedTv -> items(tmdb.topRatedTv().results, MediaType.Tv)
        RowSource.OnTheAir -> items(tmdb.onTheAir().results, MediaType.Tv)
        RowSource.NowPlaying -> items(tmdb.nowPlaying(region.region()).results, MediaType.Movie)
        RowSource.UpcomingMovies ->
            items(tmdb.upcomingMovies(region.region()).results, MediaType.Movie)
                .filter { it.isUpcoming(clock.todayIso()) }
        RowSource.UpcomingTv ->
            items(tmdb.discoverTv(firstAirDateFrom = clock.todayIso()).results, MediaType.Tv)

        // The one row this app can play in full. The ceiling is the resolver's own cutoff,
        // so every title here is one `ArchiveFreeSourceRepository` will actually look up.
        // Its vote floor is lower than the genre rows': a 1920s film with 200+ TMDB votes
        // is a short list, and the well-known silents clear 100 comfortably.
        RowSource.PublicDomain -> items(
            tmdb.discoverMovies(
                releasedTo = ArchiveFreeSourceRepository.PUBLIC_DOMAIN_CUTOFF_DATE,
                minVotes = MIN_VOTES_FOR_PUBLIC_DOMAIN,
                sortBy = TmdbApi.SORT_POPULAR,
            ).results,
            MediaType.Movie,
        )

        // Genre-seeded rows. `vote_count.gte` is what keeps these from being a wall of
        // unrated obscurities, which is what an unfiltered discover query returns.
        RowSource.Documentaries -> items(
            tmdb.discoverMovies(
                withGenres = TmdbGenres.ID_DOCUMENTARY.toString(),
                minVotes = MIN_VOTES_FOR_ROW,
            ).results,
            MediaType.Movie,
        )
        RowSource.Animation -> items(
            tmdb.discoverMovies(
                withGenres = TmdbApi.GENRE_ANIMATION,
                minVotes = MIN_VOTES_FOR_ROW,
            ).results,
            MediaType.Movie,
        )

        RowSource.AnimeSeason -> animeQuery(
            AniListQueries.SEASON_NOW,
            mapOf(
                "season" to AniListQueries.seasonFor(clock.month()),
                "year" to clock.year(),
                "perPage" to MAX_ROW_ITEMS,
            ),
        )
        RowSource.AnimeTop ->
            animeQuery(AniListQueries.TOP_RATED, mapOf("perPage" to MAX_ROW_ITEMS))
        RowSource.AnimeUpcoming ->
            animeQuery(AniListQueries.UPCOMING, mapOf("perPage" to MAX_ROW_ITEMS))
    }

    /** Every TMDB list goes through here, so none of them can forget the genre namer. */
    private fun items(dtos: List<TmdbSummaryDto>, fallbackType: MediaType?): List<MediaItem> =
        TmdbMappers.toItems(dtos, fallbackType, genreNames)

    private suspend fun animeQuery(query: String, variables: Map<String, Any?>): List<MediaItem> =
        AniListMappers.toItems(
            aniList.query(AniListRequest(query, variables)).data?.page?.media.orEmpty(),
        )

    // ---------------------------------------------------------------- detail

    override suspend fun detail(id: Int, type: MediaType, forceRefresh: Boolean): MediaDetail? {
        runCatching { genres.ensureLoaded() }

        val fetched = runCatching {
            when (type) {
                MediaType.Movie ->
                    TmdbMappers.toDetail(tmdb.movieDetail(id), region.region(), genreNames)
                MediaType.Tv ->
                    TmdbMappers.toDetail(tmdb.tvDetail(id), region.region(), genreNames)
                MediaType.Anime -> animeDetail(id)
            }
        }.getOrNull()

        if (fetched != null) {
            withContext(io) {
                dao.upsertItems(listOf(MediaMapping.toEntity(fetched.item, clock.nowMillis())))
            }
            return fetched
        }

        // Offline: the cached summary is enough to draw the screen without its extras.
        val cached = withContext(io) { dao.item(MediaItem.key(id, type)) } ?: return null
        val item = MediaMapping.toItem(cached) ?: return null
        return MediaDetail(
            item = item,
            tagline = null,
            runtimeMinutes = null,
            seasonCount = null,
            episodeCount = null,
            status = null,
            cast = emptyList(),
            trailers = emptyList(),
            seasons = emptyList(),
            similar = emptyList(),
            watch = WatchOptions.empty(region.region()),
        )
    }

    /**
     * Anime detail is AniList for the metadata plus a TMDB title lookup for the providers,
     * because AniList has no where-to-watch data at all. The lookup is best-effort: no
     * match simply means no provider row, which is better than blocking the screen.
     */
    private suspend fun animeDetail(id: Int): MediaDetail? = coroutineScope {
        val response = aniList.query(AniListRequest(AniListQueries.DETAIL, mapOf("id" to id)))
        val dto = response.data?.media ?: return@coroutineScope null
        val title = dto.title?.display ?: return@coroutineScope null

        val watch = async {
            runCatching { providersForAnime(title) }.getOrDefault(WatchOptions.empty(region.region()))
        }
        AniListMappers.toDetail(dto, watch.await())
    }

    private suspend fun providersForAnime(title: String): WatchOptions {
        val match = tmdb.searchMulti(title).results
            .firstOrNull { MediaType.fromTmdb(it.mediaType) != null }
            ?: return WatchOptions.empty(region.region())
        val type = MediaType.fromTmdb(match.mediaType) ?: return WatchOptions.empty(region.region())
        val dto = if (type == MediaType.Movie) {
            tmdb.movieProviders(match.id)
        } else {
            tmdb.tvProviders(match.id)
        }
        return TmdbMappers.toWatchOptions(dto, region.region())
    }

    override suspend fun episodes(
        id: Int,
        type: MediaType,
        seasonNumber: Int,
    ): List<EpisodeSummary> {
        if (type != MediaType.Tv) return emptyList()
        return runCatching { tmdb.season(id, seasonNumber).episodes.map(TmdbMappers::toEpisode) }
            .getOrDefault(emptyList())
    }

    override suspend fun episode(
        id: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): EpisodeSummary? = runCatching {
        TmdbMappers.toEpisode(tmdb.episode(id, seasonNumber, episodeNumber))
    }.getOrNull()

    // ---------------------------------------------------------------- browse

    override suspend fun browse(query: BrowseQuery): List<MediaItem> {
        runCatching { genres.ensureLoaded() }

        val sort = when (query.sort) {
            BrowseSort.Popular -> TmdbApi.SORT_POPULAR
            BrowseSort.TopRated -> TmdbApi.SORT_RATING
            BrowseSort.Newest -> TmdbApi.SORT_NEWEST
        }
        // A rating sort needs a vote floor or it returns unknown titles with one 10/10
        // vote. Popularity is already self-limiting, so it gets no floor.
        val minVotes = TmdbApi.MIN_VOTES_FOR_RATING.takeIf { query.sort == BrowseSort.TopRated }

        return runCatching {
            when (query.type) {
                MediaType.Movie -> items(
                    tmdb.discoverMovies(
                        withGenres = query.genreId?.toString(),
                        withCompanies = query.companyId,
                        minVotes = minVotes,
                        sortBy = sort,
                        page = query.page,
                    ).results,
                    MediaType.Movie,
                )

                MediaType.Tv -> items(
                    tmdb.discoverTv(
                        withGenres = query.genreId?.toString(),
                        withNetworks = query.networkId,
                        withCompanies = query.companyId,
                        minVotes = minVotes,
                        // `primary_release_date` is a film field; the TV equivalent differs.
                        sortBy = if (query.sort == BrowseSort.Newest) SORT_TV_NEWEST else sort,
                        page = query.page,
                    ).results,
                    MediaType.Tv,
                )

                // Anime browse goes to AniList, which has genres of its own rather than
                // TMDB ids — so a TMDB genre id is not meaningful here and is ignored.
                MediaType.Anime -> animeQuery(
                    AniListQueries.TOP_RATED,
                    mapOf("perPage" to BROWSE_PAGE_SIZE),
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun collection(id: Int): MovieCollection? {
        runCatching { genres.ensureLoaded() }
        return runCatching { TmdbMappers.toCollection(tmdb.collection(id), genreNames) }
            .getOrNull()
            ?.takeIf { it.isWorthShowing }
    }

    override suspend fun person(id: Int): PersonProfile? {
        runCatching { genres.ensureLoaded() }
        return runCatching { TmdbMappers.toPerson(tmdb.person(id), genreNames) }.getOrNull()
    }

    override suspend fun studio(id: Int, isNetwork: Boolean): Studio? = runCatching {
        if (isNetwork) {
            TmdbMappers.toStudio(tmdb.network(id))
        } else {
            TmdbMappers.toStudio(tmdb.company(id))
        }
    }.getOrNull()?.takeIf { it.name.isNotBlank() }

    // ---------------------------------------------------------------- search

    override suspend fun search(query: String): List<MediaItem> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY) return emptyList()

        val results = coroutineScope {
            val tmdbResults = async {
                runCatching { items(tmdb.searchMulti(trimmed).results, null) }
                    .getOrDefault(emptyList())
            }
            val animeResults = async {
                runCatching {
                    animeQuery(AniListQueries.SEARCH, mapOf("search" to trimmed, "perPage" to 20))
                }.getOrDefault(emptyList())
            }
            SearchMerge.interleave(tmdbResults.await(), animeResults.await())
        }

        if (results.isNotEmpty()) {
            withContext(io) {
                dao.upsertItems(results.map { MediaMapping.toEntity(it, clock.nowMillis()) })
            }
            return results
        }
        // Offline, or both sources failed: fall back to what has already been seen.
        return withContext(io) { MediaMapping.toItems(dao.searchCached(trimmed)) }
    }

    // -------------------------------------------------------------- upcoming

    override suspend fun upcoming(type: MediaType?): List<MediaItem> {
        val today = clock.todayIso()
        val specs = when (type) {
            MediaType.Movie -> listOf(RowSource.UpcomingMovies)
            MediaType.Tv -> listOf(RowSource.UpcomingTv)
            MediaType.Anime -> listOf(RowSource.AnimeUpcoming)
            null -> listOf(RowSource.UpcomingMovies, RowSource.UpcomingTv, RowSource.AnimeUpcoming)
        }
        return coroutineScope {
            specs.map { source ->
                async { runCatching { fetch(RowSpecs.of(source)) }.getOrDefault(emptyList()) }
            }
                .flatMap { it.await() }
                .filter { it.releaseDate == null || it.releaseDate > today }
                .sortedBy { it.releaseDate ?: "9999" }
                .distinctBy { it.key }
        }
    }

    override suspend fun itemsByKeys(keys: List<String>): List<MediaItem> = withContext(io) {
        if (keys.isEmpty()) return@withContext emptyList()
        val byKey = MediaMapping.toItems(dao.items(keys)).associateBy { it.key }
        // Preserve the caller's order — My List is most-recently-added first.
        keys.mapNotNull { byKey[it] }
    }

    private companion object {
        /** Long enough that browsing never re-fetches, short enough to see a new release. */
        const val CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000

        const val MAX_ROW_ITEMS = 20
        const val MIN_QUERY = 2

        /** What TMDB returns per `/discover` page; the grid's page size follows it. */
        const val BROWSE_PAGE_SIZE = 20

        /**
         * A vote floor for genre-seeded rows. Without it `/discover` leads with titles
         * nobody has rated, which reads as a broken row rather than a curated one.
         */
        const val MIN_VOTES_FOR_ROW = 200

        /** See `RowSource.PublicDomain` — pre-1930 films are a much smaller voting pool. */
        const val MIN_VOTES_FOR_PUBLIC_DOMAIN = 100

        /** `primary_release_date` is film-only; series sort on first air date. */
        const val SORT_TV_NEWEST = "first_air_date.desc"
    }
}
