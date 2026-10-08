package com.example.streamingappzb.ui

import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.FakeCatalogRepository
import com.example.streamingappzb.domain.FakeDownloadRepository
import com.example.streamingappzb.domain.FakeMyListRepository
import com.example.streamingappzb.domain.FakeNetworkRepository
import com.example.streamingappzb.domain.FakeProgressRepository
import com.example.streamingappzb.domain.FakeSettingsRepository
import com.example.streamingappzb.domain.RecordingAnalytics
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import com.example.streamingappzb.ui.home.ChipKey
import com.example.streamingappzb.ui.home.HomeItem
import com.example.streamingappzb.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Home's reactive wiring.
 *
 * These go through the real ViewModel rather than the use case because the bug they guard
 * against is a **StateFlow conflation** one: adding the hero's title to My List leaves the
 * feed itself byte-identical, so a screen that rebuilds the feed and reads My List
 * imperatively silently shows stale state. That is only observable when something actually
 * collects the flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val catalog = FakeCatalogRepository(
        rows = listOf(
            HomeRow(
                key = HomeRow.KEY_NEW,
                fallbackTitle = "New episodes",
                titles = listOf(FakeCatalogRepository.SERIES, FakeCatalogRepository.FILM),
            ),
        ),
    )
    private val progress = FakeProgressRepository()
    private val myList = FakeMyListRepository()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    /**
     * A scope on the test's own dispatcher, deliberately *not* `runTest`'s `backgroundScope`
     * — coroutines started there do not drive [SessionState]'s eagerly-shared flows under
     * `advanceUntilIdle`, so every late repository emission would be silently invisible and
     * these tests would pass against a broken app. Detached via its own SupervisorJob so
     * `runTest` does not wait on it; cancelled in [tearDown].
     */
    private val scopes = mutableListOf<CoroutineScope>()

    private fun TestScope.newScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob()).also { scopes += it }

    private fun TestScope.viewModel(network: NetworkState = NetworkState.Wifi): HomeViewModel {
        val scope = newScope()
        val vm = HomeViewModel(
            getHomeFeed = GetHomeFeedUseCase(catalog, progress),
            catalog = catalog,
            myList = myList,
            session = SessionState(
                settings = FakeSettingsRepository(),
                network = FakeNetworkRepository(network),
                progress = progress,
                myList = myList,
                downloads = FakeDownloadRepository(),
                resolveQuality = ResolveQualityUseCase(catalog),
                scope = scope,
            ),
            analytics = analytics,
        )
        // `state` is WhileSubscribed, so nothing runs until something collects.
        scope.launch { vm.state.collect {} }
        return vm
    }

    private fun hero(vm: HomeViewModel) =
        vm.state.value.items.filterIsInstance<HomeItem.Hero>().single()

    @Test
    fun `adding the hero to My List turns its button into a tick`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(FakeCatalogRepository.SERIES.id, hero(vm).title.id)
        assertFalse("nothing saved yet", hero(vm).inMyList)

        vm.toggleMyList(FakeCatalogRepository.SERIES.id)
        advanceUntilIdle()

        // The feed is unchanged by this — only My List moved. If My List is read at map
        // time instead of being combined in, the StateFlow conflates and this stays false.
        assertTrue("the hero must reflect My List", hero(vm).inMyList)

        vm.toggleMyList(FakeCatalogRepository.SERIES.id)
        advanceUntilIdle()
        assertFalse("and must come back off", hero(vm).inMyList)
    }

    @Test
    fun `a toggle made elsewhere shows on the hero`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        // The Title screen and a poster long-press both go straight to the repository.
        myList.toggle(FakeCatalogRepository.SERIES.id)
        advanceUntilIdle()

        assertTrue(hero(vm).inMyList)
    }

    @Test
    fun `the toggle is reported once per tap, with the resulting state`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        analytics.clear()

        vm.toggleMyList(1)
        advanceUntilIdle()
        vm.toggleMyList(1)
        advanceUntilIdle()

        assertEquals(
            listOf("mylist_toggle{id=1, on=true}", "mylist_toggle{id=1, on=false}"),
            analytics.events,
        )
    }

    @Test
    fun `progress saved elsewhere appears in Continue watching`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(
            "nothing watched yet",
            vm.state.value.items.none { it is HomeItem.Continue },
        )

        progress.put(
            Progress(
                titleId = FakeCatalogRepository.SERIES.id,
                episode = 2,
                positionSeconds = 300,
                updatedAt = 1_000L,
            ),
        )
        advanceUntilIdle()

        val cont = vm.state.value.items.filterIsInstance<HomeItem.Continue>().single()
        assertEquals(FakeCatalogRepository.SERIES.id, cont.items.single().title.id)
    }

    @Test
    fun `a chip filters the rows`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectChip(Kind.Film.name)
        advanceUntilIdle()

        val row = vm.state.value.items.filterIsInstance<HomeItem.Row>().single()
        assertEquals(listOf(FakeCatalogRepository.FILM.id), row.row.titles.map { it.id })
        assertEquals(Kind.Film.name, vm.state.value.chip)
    }

    @Test
    fun `loading is only true before the first feed arrives`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue("nothing has been built yet", vm.state.value.loading)

        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `the network the session reports is what Home renders`() = runTest(dispatcher) {
        val vm = viewModel(network = NetworkState.Cellular)
        advanceUntilIdle()

        assertEquals(NetworkState.Cellular, vm.state.value.network)
        // Data Saver defaults on, which is what puts "Saver" in the app-bar pill.
        assertTrue(vm.state.value.saver)
        assertEquals(ChipKey.ALL, vm.state.value.chip)
    }
}
