package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.db.TmdbGenreDao
import com.example.streamingappzb.data.db.TmdbGenreEntity
import com.example.streamingappzb.data.remote.tmdb.TmdbApi
import com.example.streamingappzb.data.remote.tmdb.TmdbGenres
import com.example.streamingappzb.data.remote.tmdb.TmdbMappers
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.GenreScope
import com.example.streamingappzb.domain.repository.GenreRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * TMDB's genre vocabulary, fetched once and cached.
 *
 * Replaces the hardcoded id→name map as the *source*, while keeping it as the fallback.
 * That ordering matters: genre names are needed to draw the first poster subtitle, so a
 * cold open cannot wait on a network call for them. The cache answers in microseconds, the
 * fallback answers instantly, and the fetch happens behind both.
 *
 * ### Why the ids are cached at all
 *
 * The names are the visible half. The other half is the id list for the browse grid: that
 * used to be a filtered view of the hardcoded map, which meant the grid could not show a
 * genre TMDB added, and showed television genres on the film tab because the map had no
 * scope. Both are fixed by having the real, scoped lists.
 */
class GenreRepositoryImpl(
    private val tmdb: TmdbApi,
    private val dao: TmdbGenreDao,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : GenreRepository {

    /** In-memory, because `name()` is called once per poster and must not touch disk. */
    @Volatile
    private var names: Map<String, String> = emptyMap()

    /** Serialises the refresh so a cold Home drawing four rows does not fetch four times. */
    private val refreshLock = Mutex()

    override suspend fun ensureLoaded() {
        if (names.isNotEmpty() && !isStale()) return
        refreshLock.withLock {
            // A second caller that queued on the lock while the first fetched is done.
            if (names.isNotEmpty() && !isStale()) return

            val cached = withContext(io) { dao.all() }
            if (cached.isNotEmpty()) publish(cached)
            if (cached.isEmpty() || isStale(cached)) runCatching { refresh() }
        }
    }

    override suspend fun genres(scope: GenreScope): List<MediaGenre> {
        ensureLoaded()
        val cached = withContext(io) { dao.forScope(scope.name) }
        if (cached.isNotEmpty()) return cached.map { it.toGenre() }
        // Nothing cached and the fetch failed: the built-in list is better than an empty
        // browse grid, and it is correct for every genre TMDB has had for years.
        return TmdbGenres.fallback(scope)
    }

    override fun name(id: Int): String? =
        names[MediaGenre(id, "", GenreScope.Movie).key]
            ?: names[MediaGenre(id, "", GenreScope.Tv).key]
            ?: TmdbGenres.name(id)

    override fun names(ids: List<Int>, limit: Int): List<String> =
        ids.mapNotNull(::name).distinct().take(limit)

    private suspend fun refresh() = coroutineScope {
        // Both lists, concurrently. One failing leaves the other usable rather than
        // losing the whole vocabulary.
        val movies = async {
            runCatching { TmdbMappers.toGenres(tmdb.movieGenres(), GenreScope.Movie) }
                .getOrDefault(emptyList())
        }
        val series = async {
            runCatching { TmdbMappers.toGenres(tmdb.tvGenres(), GenreScope.Tv) }
                .getOrDefault(emptyList())
        }

        val fetched = movies.await() + series.await()
        if (fetched.isEmpty()) return@coroutineScope

        val now = clock.nowMillis()
        val entities = fetched.map { it.toEntity(now) }
        withContext(io) { dao.upsertAll(entities) }
        publish(entities)
    }

    private fun publish(entities: List<TmdbGenreEntity>) {
        names = entities.associate { it.key to it.name }
    }

    private suspend fun isStale(): Boolean {
        val oldest = withContext(io) { dao.oldestTimestamp() } ?: return true
        return clock.nowMillis() - oldest > TTL_MILLIS
    }

    private fun isStale(cached: List<TmdbGenreEntity>): Boolean {
        val oldest = cached.minOfOrNull { it.cachedAt } ?: return true
        return clock.nowMillis() - oldest > TTL_MILLIS
    }

    private fun MediaGenre.toEntity(now: Long) = TmdbGenreEntity(
        key = key,
        genreId = id,
        name = name,
        scope = scope.name,
        cachedAt = now,
    )

    private fun TmdbGenreEntity.toGenre() = MediaGenre(
        id = genreId,
        name = name,
        scope = runCatching { GenreScope.valueOf(scope) }.getOrDefault(GenreScope.Movie),
    )

    private companion object {
        /**
         * Thirty days. TMDB's genre list changes perhaps once a year, so anything shorter
         * is a wasted round trip; anything longer and a new genre reads as blank for a
         * season.
         */
        const val TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
