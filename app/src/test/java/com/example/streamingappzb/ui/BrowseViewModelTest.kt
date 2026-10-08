package com.example.streamingappzb.ui

import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.BrowseSort
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.repository.GenreRepository
import com.example.streamingappzb.domain.repository.MediaListRepository
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.usecase.GetBrowseResultsUseCase
import com.example.streamingappzb.ui.browse.BrowseViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The browse grid's paging and sorting.
 *
 * Three behaviours carry real risk here and none of them are visible from the happy path:
 * a page arriving twice must not duplicate a poster, a short page must stop the paging, and
 * changing the sort must restart from page one rather than re-ordering what is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first page loads and the heading is kept`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = FULL_PAGE)
        val vm = viewModel(media)
        advanceUntilIdle()

        assertEquals("Science Fiction", vm.state.value.heading)
        assertEquals(FULL_PAGE, vm.state.value.items.size)
        assertFalse(vm.state.value.loading)
        assertEquals(listOf(1), media.requestedPages)
    }

    @Test
    fun `loadMore appends the next page`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = FULL_PAGE)
        val vm = viewModel(media)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), media.requestedPages)
        assertEquals(FULL_PAGE * 2, vm.state.value.items.size)
        assertFalse(vm.state.value.appending)
    }

    @Test
    fun `a title repeated across pages appears once`() = runTest(dispatcher) {
        // TMDB's popularity ordering shifts between requests, so page 2 genuinely can
        // repeat an item from page 1.
        val media = FakeMedia(pageSize = FULL_PAGE, overlap = 5)
        val vm = viewModel(media)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        val keys = vm.state.value.items.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(FULL_PAGE * 2 - 5, keys.size)
    }

    @Test
    fun `a short page ends the paging`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = 7)
        val vm = viewModel(media)
        advanceUntilIdle()

        assertTrue(vm.state.value.exhausted)

        vm.loadMore()
        advanceUntilIdle()

        // Exhausted, so no second request was made.
        assertEquals(listOf(1), media.requestedPages)
    }

    @Test
    fun `changing the sort restarts at page one`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = FULL_PAGE)
        val vm = viewModel(media)
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        vm.setSort(BrowseSort.TopRated)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 1), media.requestedPages)
        assertEquals(BrowseSort.TopRated, vm.state.value.sort)
        // Replaced, not appended — these are a different ordering of the catalogue.
        assertEquals(FULL_PAGE, vm.state.value.items.size)
        assertEquals(BrowseSort.TopRated, media.lastQuery?.sort)
    }

    @Test
    fun `re-selecting the current sort is a no-op`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = FULL_PAGE)
        val vm = viewModel(media)
        advanceUntilIdle()

        vm.setSort(BrowseSort.Popular)
        advanceUntilIdle()

        assertEquals(listOf(1), media.requestedPages)
    }

    @Test
    fun `an empty first page is reported as empty, not as still loading`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = 0)
        val vm = viewModel(media)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.exhausted)
    }

    @Test
    fun `the genre id and type reach the query`() = runTest(dispatcher) {
        val media = FakeMedia(pageSize = FULL_PAGE)
        viewModel(media, genreId = 10765, type = MediaType.Tv)
        advanceUntilIdle()

        assertEquals(10765, media.lastQuery?.genreId)
        assertEquals(MediaType.Tv, media.lastQuery?.type)
        assertEquals(null, media.lastQuery?.networkId)
    }

    // ------------------------------------------------------------------ setup

    private fun viewModel(
        media: FakeMedia,
        genreId: Int? = 878,
        type: MediaType = MediaType.Movie,
    ) = BrowseViewModel(
        genreId = genreId,
        companyId = null,
        networkId = null,
        type = type,
        heading = "Science Fiction",
        browse = GetBrowseResultsUseCase(media, mockk<GenreRepository>(relaxed = true)),
        myList = FakeMyList(),
        analytics = mockk(relaxed = true),
    )

    /**
     * @param overlap how many of the next page's items repeat the previous page's, which is
     *   what a shifting popularity order looks like from the client.
     */
    private class FakeMedia(
        private val pageSize: Int,
        private val overlap: Int = 0,
    ) : MediaRepository by mockk(relaxed = true) {

        val requestedPages = mutableListOf<Int>()
        var lastQuery: BrowseQuery? = null
            private set

        override suspend fun browse(query: BrowseQuery): List<MediaItem> {
            requestedPages += query.page
            lastQuery = query
            val first = (query.page - 1) * pageSize - (query.page - 1) * overlap
            return (0 until pageSize).map { offset -> item(first + offset) }
        }

        private fun item(id: Int) = MediaItem(
            id = id,
            type = MediaType.Movie,
            title = "Title $id",
            overview = "",
            posterPath = null,
            backdropPath = null,
            year = 2024,
            rating = null,
        )
    }

    private class FakeMyList : MediaListRepository {
        override fun observeKeys(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun keys(): List<String> = emptyList()
        override suspend fun contains(item: MediaItem): Boolean = false
        override suspend fun toggle(item: MediaItem): Boolean = true
    }

    private companion object {
        /** `GetBrowseResultsUseCase` treats anything short of this as the end. */
        const val FULL_PAGE = 20
    }
}
