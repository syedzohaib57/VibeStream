package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.HomeFeed
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.repository.ProgressRepository
import com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance item 3: **Home never shows more than eight rows (Continue watching counts),
 * and filtered-out rows disappear.**
 */
class HomeFeedTest {

    private class StubProgress(private val items: List<Progress>) : ProgressRepository {
        override fun observeAll(): Flow<List<Progress>> = flowOf(items)
        override suspend fun all(): List<Progress> = items
        override suspend fun get(titleId: Int): Progress? = items.firstOrNull { it.titleId == titleId }
        override suspend fun put(progress: Progress) = Unit
        override suspend fun delete(titleId: Int) = Unit
    }

    private val series = FakeCatalogRepository.SERIES
    private val film = FakeCatalogRepository.FILM
    private val streamOnly = FakeCatalogRepository.STREAM_ONLY

    private fun catalogue(rows: List<HomeRow>) = FakeCatalogRepository(
        titles = listOf(series, film, streamOnly),
        rows = rows,
        heroByKind = mapOf(
            "all" to series.id,
            Kind.Film.name to film.id,
            Kind.Drama.name to series.id,
        ),
    )

    private fun row(key: String, vararg titles: com.example.streamingappzb.domain.model.Title) =
        HomeRow(key = key, fallbackTitle = key, titles = titles.toList())

    @Test
    fun `a row emptied by the chip filter is dropped, never rendered empty`() = runTest {
        val useCase = GetHomeFeedUseCase(
            catalogue(listOf(row("dramas", series, streamOnly), row("films", film))),
            StubProgress(emptyList()),
        )

        val filmsOnly = useCase(Kind.Film)
        assertEquals(listOf("films"), filmsOnly.rows.map { it.key })

        val dramasOnly = useCase(Kind.Drama)
        assertEquals(listOf("dramas"), dramasOnly.rows.map { it.key })
        assertTrue(dramasOnly.rows.all { it.titles.isNotEmpty() })
    }

    @Test
    fun `at most eight rows, and Continue watching counts as one of them`() = runTest {
        val manyRows = (1..12).map { row("row$it", series) }

        val withoutContinue = GetHomeFeedUseCase(catalogue(manyRows), StubProgress(emptyList()))(null)
        assertTrue(withoutContinue.continueWatching.isEmpty())
        assertEquals(HomeFeed.MAX_ROWS, withoutContinue.rows.size)

        val withContinue = GetHomeFeedUseCase(
            catalogue(manyRows),
            StubProgress(listOf(Progress(series.id, 1, 600, 0L))),
        )(null)
        assertEquals(1, withContinue.continueWatching.size)
        assertEquals(
            "Continue watching occupies one of the eight",
            HomeFeed.MAX_ROWS - 1,
            withContinue.rows.size,
        )
        assertEquals(
            HomeFeed.MAX_ROWS,
            withContinue.rows.size + withContinue.continueWatching.size.coerceAtMost(1),
        )
    }

    @Test
    fun `the chip swaps the hero`() = runTest {
        val useCase = GetHomeFeedUseCase(catalogue(emptyList()), StubProgress(emptyList()))
        assertEquals(series.id, useCase(null).hero?.id)
        assertEquals(film.id, useCase(Kind.Film).hero?.id)
    }

    @Test
    fun `Continue watching is filtered by the chip as well as the rows`() = runTest {
        val progress = listOf(
            Progress(series.id, 1, 600, 2L),
            Progress(film.id, 1, 600, 1L),
        )
        val useCase = GetHomeFeedUseCase(catalogue(emptyList()), StubProgress(progress))

        assertEquals(2, useCase(null).continueWatching.size)
        assertEquals(listOf(film.id), useCase(Kind.Film).continueWatching.map { it.title.id })
        assertEquals(listOf(series.id), useCase(Kind.Drama).continueWatching.map { it.title.id })
    }

    @Test
    fun `the hero carries its own saved progress so the button can read Resume`() = runTest {
        val useCase = GetHomeFeedUseCase(
            catalogue(emptyList()),
            StubProgress(listOf(Progress(series.id, 2, 900, 0L))),
        )
        val feed = useCase(null)
        assertEquals(2, feed.heroProgress?.episode)
        assertEquals(900, feed.heroProgress?.positionSeconds)
    }

    @Test
    fun `a Continue card knows its fraction and the minutes left`() = runTest {
        val useCase = GetHomeFeedUseCase(
            catalogue(emptyList()),
            StubProgress(listOf(Progress(series.id, 1, 1145, 0L))),
        )
        val card = useCase(null).continueWatching.single()
        // Episode one is 38:10 = 2290s, so half way through.
        assertEquals(0.5f, card.fraction, 0.01f)
        // Rounded up, so something still playing never reads "0 min left".
        assertEquals(20, card.minutesLeft)
    }

    @Test
    fun `New episodes only ever appears on a fresh series`() {
        val freshSeries = FakeCatalogRepository.title(20, "Heartland", fresh = true)
        val freshFilm = FakeCatalogRepository.title(
            id = 21,
            name = "A Film",
            kind = Kind.Film,
            episodes = null,
            minutes = 100,
            fresh = true,
        )
        assertTrue(freshSeries.showsNewEpisodes)
        // A film cannot have new episodes, however fresh it is (acceptance item 4).
        assertTrue(!freshFilm.showsNewEpisodes)
        assertTrue(!series.showsNewEpisodes)
    }
}
