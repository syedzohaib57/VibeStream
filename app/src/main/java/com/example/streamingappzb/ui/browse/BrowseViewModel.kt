package com.example.streamingappzb.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.BrowseSort
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.repository.MediaListRepository
import com.example.streamingappzb.domain.usecase.GetBrowseResultsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val heading: String = "",
    val items: List<MediaItem> = emptyList(),
    val sort: BrowseSort = BrowseSort.Popular,
    val savedKeys: List<String> = emptyList(),
    /** First page only. Later pages use [appending] so the grid does not blank out. */
    val loading: Boolean = true,
    val appending: Boolean = false,
    val exhausted: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !loading
}

/**
 * A browse grid: everything in a genre, from a studio, or on a network.
 *
 * ### Paging without a paging library
 *
 * The grid asks for the next page when it nears the bottom and appends. There is no
 * `Pager` here because the source is stateless and read-only — a page is a plain request
 * keyed by a number, and the whole of what Paging would add is the invalidation machinery
 * this screen has nothing to invalidate.
 *
 * Changing the sort resets to page one rather than re-sorting what is loaded: sorting three
 * loaded pages locally would show "the top rated of the first sixty popular titles", which
 * is not what the control says.
 */
class BrowseViewModel(
    private val genreId: Int?,
    private val companyId: Int?,
    private val networkId: Int?,
    private val type: MediaType,
    heading: String,
    private val browse: GetBrowseResultsUseCase,
    private val myList: MediaListRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(heading = heading))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var page = 1
    private var inFlight: Job? = null

    init {
        viewModelScope.launch {
            myList.observeKeys().collect { keys ->
                _state.value = _state.value.copy(savedKeys = keys)
            }
        }
        load(reset = true)
    }

    fun setSort(next: BrowseSort) {
        if (_state.value.sort == next) return
        _state.value = _state.value.copy(sort = next)
        load(reset = true)
    }

    /** Called as the grid nears its end. Ignored while a page is already in flight. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.appending || current.exhausted) return
        load(reset = false)
    }

    fun retry() = load(reset = _state.value.items.isEmpty())

    private fun load(reset: Boolean) {
        inFlight?.cancel()
        if (reset) {
            page = 1
            _state.value = _state.value.copy(loading = true, appending = false, exhausted = false)
        } else {
            page += 1
            _state.value = _state.value.copy(appending = true)
        }

        val query = BrowseQuery(
            type = type,
            genreId = genreId,
            companyId = companyId,
            networkId = networkId,
            sort = _state.value.sort,
            page = page,
        )

        inFlight = viewModelScope.launch {
            val fetched = browse(query)
            val current = _state.value
            _state.value = current.copy(
                // Dedupe across pages: TMDB's popularity ordering shifts between requests,
                // so the same title can arrive on two pages and would render twice.
                items = if (reset) {
                    fetched
                } else {
                    (current.items + fetched).distinctBy { it.key }
                },
                loading = false,
                appending = false,
                exhausted = !browse.hasMore(query, fetched.size),
            )
            if (reset) analytics.chipSelect("browse:${query.key}")
        }
    }

    fun toggleMyList(item: MediaItem) {
        viewModelScope.launch {
            val added = myList.toggle(item)
            analytics.myListToggle(item.id, added)
        }
    }

    fun onOpened(item: MediaItem) = analytics.titleView(item.id)
}
