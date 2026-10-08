package com.example.streamingappzb.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.ContinueItem
import com.example.streamingappzb.domain.model.HomeFeed
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of Home's vertical list. */
sealed interface HomeItem {
    data class Hero(
        val title: Title,
        val progress: Progress?,
        val inMyList: Boolean,
    ) : HomeItem

    data class Continue(val items: List<ContinueItem>) : HomeItem

    data class Row(val row: HomeRow) : HomeItem
}

data class HomeUiState(
    val chip: String = ChipKey.ALL,
    val items: List<HomeItem> = emptyList(),
    val network: NetworkState = NetworkState.Cellular,
    val saver: Boolean = true,
    val activeDownloads: Int = 0,
    val loading: Boolean = true,
)

/** The Home chips, as keys rather than enums so the "All" case needs no null handling. */
object ChipKey {
    const val ALL = "all"

    fun toKind(key: String): Kind? = if (key == ALL) null else Kind.from(key)
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
    private val session: SessionState,
    private val analytics: Analytics,
) : ViewModel() {

    private val chip = MutableStateFlow(ChipKey.ALL)
    private val feed = MutableStateFlow<HomeFeed?>(null)

    /**
     * Null until the first feed lands, which is what [HomeUiState.loading] reads.
     *
     * My List is combined in here rather than read from [SessionState] at map time. It has
     * to be a real input: adding the hero's title leaves [HomeFeed] itself unchanged, so
     * re-assigning it would be conflated away by the StateFlow and the hero button would
     * never turn into a tick.
     */
    private val items: Flow<List<HomeItem>?> =
        combine(feed, session.myListIds) { homeFeed, myListIds -> homeFeed?.toItems(myListIds) }

    val state: StateFlow<HomeUiState> = combine(
        chip,
        items,
        session.networkState,
        session.saver,
        session.activeDownloadCount,
    ) { chipKey, homeItems, network, saver, downloads ->
        HomeUiState(
            chip = chipKey,
            items = homeItems.orEmpty(),
            network = network,
            saver = saver,
            activeDownloads = downloads,
            loading = homeItems == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private val _toast = MutableStateFlow<Int?>(null)
    val toast: StateFlow<Int?> = _toast.asStateFlow()

    init {
        analytics.homeView(ChipKey.ALL)
        refresh()

        // Continue watching and the catalogue can both change while Home is on screen —
        // finishing an episode elsewhere, the first seed landing — so the feed is rebuilt
        // whenever either emits. My List is not here: it feeds [items] directly.
        viewModelScope.launch {
            combine(
                session.continueWatching,
                catalog.observeTitles(),
            ) { _, titles -> titles.size }.collect { refresh() }
        }
    }

    fun selectChip(key: String) {
        if (chip.value == key) return
        chip.value = key
        analytics.chipSelect(key)
        analytics.homeView(key)
        refresh()
    }

    fun toggleMyList(titleId: Int) {
        viewModelScope.launch {
            val added = myList.toggle(titleId)
            analytics.myListToggle(titleId, added)
            _toast.value = if (added) {
                com.example.streamingappzb.R.string.toast_added_to_list
            } else {
                com.example.streamingappzb.R.string.toast_removed_from_list
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun onHeroPlay(titleId: Int, resuming: Boolean) = analytics.heroPlay(titleId, resuming)

    fun onTitleOpened(titleId: Int) = analytics.titleView(titleId)

    private fun refresh() {
        viewModelScope.launch {
            // Cheap and idempotent after the first run; makes Home correct on cold start
            // even if the Application's seed coroutine has not finished yet.
            catalog.ensureSeeded()
            feed.value = getHomeFeed(ChipKey.toKind(chip.value))
        }
    }

    private fun HomeFeed.toItems(myListIds: List<Int>): List<HomeItem> = buildList {
        hero?.let { add(HomeItem.Hero(it, heroProgress, myListIds.contains(it.id))) }
        if (continueWatching.isNotEmpty()) add(HomeItem.Continue(continueWatching))
        rows.forEach { add(HomeItem.Row(it)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
