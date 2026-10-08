package com.example.streamingappzb.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.remote.tmdb.TmdbCredentials
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.MediaFeed
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaRow
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.repository.MediaListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One entry in the vertical list. */
sealed interface DiscoverItem {
    data class Hero(val item: MediaItem, val inMyList: Boolean) : DiscoverItem

    data class Row(val row: MediaRow) : DiscoverItem
}

data class DiscoverUiState(
    val tab: MediaType? = null,
    val items: List<DiscoverItem> = emptyList(),
    val network: NetworkState = NetworkState.Cellular,
    val saver: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Set when the build has no TMDB key — the one failure worth naming precisely. */
    val needsApiKey: Boolean = false,
    /** Cache was served but the refresh failed; the screen is usable but stale. */
    val staleOffline: Boolean = false,
)

/**
 * Discover, over real catalogue data.
 *
 * The tab is part of the cache key rather than a filter applied after loading: each tab
 * asks different endpoints (a Series tab has no "in cinemas" row), so filtering one shared
 * feed would either show empty rows or hide rows the tab should have.
 */
class DiscoverViewModel(
    private val media: MediaRepository,
    private val myList: MediaListRepository,
    private val session: SessionState,
    private val analytics: Analytics,
) : ViewModel() {

    private val tab = MutableStateFlow<MediaType?>(null)
    private val feed = MutableStateFlow(MediaFeed.EMPTY)
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<Throwable?>(null)

    val state: StateFlow<DiscoverUiState> = combine(
        tab,
        combine(feed, myList.observeKeys()) { f, keys -> f to keys },
        combine(loading, refreshing, failure) { l, r, e -> Triple(l, r, e) },
        session.networkState,
        session.saver,
    ) { currentTab, (currentFeed, savedKeys), (isLoading, isRefreshing, error), network, saver ->
        DiscoverUiState(
            tab = currentTab,
            items = currentFeed.toItems(savedKeys),
            network = network,
            saver = saver,
            loading = isLoading && currentFeed.isEmpty,
            refreshing = isRefreshing,
            // Only when there is genuinely nothing to show. AniList needs no token, so the
            // anime rows populate on a tokenless build — covering them with a full-screen
            // setup notice would hide data that is right there and working.
            needsApiKey = !TmdbCredentials.isPresent && currentFeed.isEmpty && !isLoading,
            staleOffline = error != null && !currentFeed.isEmpty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiscoverUiState())

    private val _toast = MutableStateFlow<Int?>(null)
    val toast: StateFlow<Int?> = _toast.asStateFlow()

    init {
        analytics.homeView(TAB_ALL)
        load(force = false)
    }

    fun selectTab(next: MediaType?) {
        if (tab.value == next) return
        tab.value = next
        val key = next?.name ?: TAB_ALL
        analytics.chipSelect(key)
        analytics.homeView(key)
        // A tab the viewer has visited before renders from cache instantly; a new one
        // shows the spinner only until its first row lands.
        feed.value = MediaFeed.EMPTY
        load(force = false)
    }

    fun refresh() {
        if (refreshing.value) return
        load(force = true)
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            val current = tab.value
            if (force) refreshing.value = true else loading.value = true
            runCatching { media.feed(current, forceRefresh = force) }
                .onSuccess { result ->
                    // A tab switch mid-flight must not overwrite the newer request.
                    if (tab.value == current) {
                        feed.value = result
                        failure.value = null
                    }
                }
                .onFailure { error ->
                    if (tab.value == current) failure.value = error
                }
            loading.value = false
            refreshing.value = false
        }
    }

    fun toggleMyList(item: MediaItem) {
        viewModelScope.launch {
            val added = myList.toggle(item)
            analytics.myListToggle(item.id, added)
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

    fun onOpened(item: MediaItem) = analytics.titleView(item.id)

    private fun MediaFeed.toItems(savedKeys: List<String>): List<DiscoverItem> = buildList {
        hero?.let { add(DiscoverItem.Hero(it, savedKeys.contains(it.key))) }
        rows.filterNot { it.isEmpty }.forEach { add(DiscoverItem.Row(it)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TAB_ALL = "all"
    }
}
