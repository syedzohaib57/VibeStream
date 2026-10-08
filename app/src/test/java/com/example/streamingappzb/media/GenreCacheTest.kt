package com.example.streamingappzb.media

import com.example.streamingappzb.data.db.TmdbGenreDao
import com.example.streamingappzb.data.db.TmdbGenreEntity
import com.example.streamingappzb.data.media.Clock
import com.example.streamingappzb.data.media.GenreRepositoryImpl
import com.example.streamingappzb.data.remote.tmdb.TmdbApi
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreListDto
import com.example.streamingappzb.domain.model.GenreScope
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The genre vocabulary: fetched from TMDB, cached in Room, with the built-in map as the
 * cold-start fallback.
 *
 * The thing worth testing is not the mapping — that is covered elsewhere — but the three
 * paths through the cache: the first ever call, a warm call that must not re-fetch, and a
 * cold call with no network, which has to answer *something* because genre names are needed
 * to draw a poster subtitle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenreCacheTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `the first call fetches both spaces and caches them`() = runTest(dispatcher) {
        val api = FakeApi()
        val dao = FakeDao()
        val repo = GenreRepositoryImpl(api, dao, FixedClock(NOW), dispatcher)

        repo.ensureLoaded()

        assertEquals(1, api.movieCalls)
        assertEquals(1, api.tvCalls)
        assertEquals("Science Fiction", repo.name(878))
        assertEquals("Sci-Fi & Fantasy", repo.name(10765))
        // Both spaces are in one table, keyed so the ids cannot collide.
        assertEquals(setOf("Movie:878", "Movie:16", "Tv:10765"), dao.rows.keys)
    }

    @Test
    fun `a warm cache does not re-fetch`() = runTest(dispatcher) {
        val api = FakeApi()
        val dao = FakeDao()
        val repo = GenreRepositoryImpl(api, dao, FixedClock(NOW), dispatcher)

        repo.ensureLoaded()
        repo.ensureLoaded()
        repo.ensureLoaded()

        assertEquals(1, api.movieCalls)
    }

    @Test
    fun `a cache older than the TTL is refreshed`() = runTest(dispatcher) {
        val api = FakeApi()
        val dao = FakeDao().apply {
            put(TmdbGenreEntity("Movie:28", 28, "Stale Action", "Movie", cachedAt = NOW))
        }
        // Thirty-one days later — past the thirty-day TTL.
        val repo = GenreRepositoryImpl(api, dao, FixedClock(NOW + 31L * DAY), dispatcher)

        repo.ensureLoaded()

        assertEquals(1, api.movieCalls)
        assertEquals("Science Fiction", repo.name(878))
    }

    @Test
    fun `a cold start with no network still answers from the built-in list`() =
        runTest(dispatcher) {
            val api = FakeApi(failing = true)
            val repo = GenreRepositoryImpl(api, FakeDao(), FixedClock(NOW), dispatcher)

            repo.ensureLoaded()

            // The fetch failed and nothing is cached, so this is the hardcoded fallback —
            // which is the whole reason it was kept.
            assertEquals("Science Fiction", repo.name(878))
            assertTrue(repo.genres(GenreScope.Movie).any { it.name == "Horror" })
        }

    @Test
    fun `a failed fetch serves whatever was already cached`() = runTest(dispatcher) {
        val api = FakeApi(failing = true)
        val dao = FakeDao().apply {
            put(TmdbGenreEntity("Movie:28", 28, "Action", "Movie", cachedAt = NOW))
        }
        val repo = GenreRepositoryImpl(api, dao, FixedClock(NOW), dispatcher)

        repo.ensureLoaded()

        assertEquals(listOf("Action"), repo.genres(GenreScope.Movie).map { it.name })
    }

    @Test
    fun `an unknown id is null rather than a blank string`() = runTest(dispatcher) {
        val repo = GenreRepositoryImpl(FakeApi(), FakeDao(), FixedClock(NOW), dispatcher)
        repo.ensureLoaded()

        assertEquals(null, repo.name(999_999))
    }

    @Test
    fun `names are deduped and capped, so a metadata line cannot wrap`() = runTest(dispatcher) {
        val repo = GenreRepositoryImpl(FakeApi(), FakeDao(), FixedClock(NOW), dispatcher)
        repo.ensureLoaded()

        // 16 is Animation in both spaces; the name must appear once.
        assertEquals(listOf("Animation"), repo.names(listOf(16, 16)))
        assertEquals(2, repo.names(listOf(878, 16, 10765), limit = 2).size)
    }

    // ------------------------------------------------------------------ fakes

    private class FakeApi(private val failing: Boolean = false) : TmdbApi by mockk(relaxed = true) {
        var movieCalls = 0
            private set
        var tvCalls = 0
            private set

        override suspend fun movieGenres(): TmdbGenreListDto {
            movieCalls++
            if (failing) throw IOException("offline")
            return TmdbGenreListDto(
                listOf(TmdbGenreDto(878, "Science Fiction"), TmdbGenreDto(16, "Animation")),
            )
        }

        override suspend fun tvGenres(): TmdbGenreListDto {
            tvCalls++
            if (failing) throw IOException("offline")
            return TmdbGenreListDto(listOf(TmdbGenreDto(10765, "Sci-Fi & Fantasy")))
        }
    }

    private class FakeDao : TmdbGenreDao {
        val rows = linkedMapOf<String, TmdbGenreEntity>()

        fun put(entity: TmdbGenreEntity) {
            rows[entity.key] = entity
        }

        override suspend fun all(): List<TmdbGenreEntity> = rows.values.sortedBy { it.name }

        override suspend fun forScope(scope: String): List<TmdbGenreEntity> =
            rows.values.filter { it.scope == scope }.sortedBy { it.name }

        override suspend fun upsertAll(genres: List<TmdbGenreEntity>) {
            genres.forEach { put(it) }
        }

        override suspend fun oldestTimestamp(): Long? = rows.values.minOfOrNull { it.cachedAt }

        override suspend fun count(): Int = rows.size
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
        override fun todayIso(): String = "2026-01-01"
        override fun month(): Int = 1
        override fun year(): Int = 2026
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_700_000_000_000L
    }
}
