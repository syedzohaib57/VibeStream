package com.example.streamingappzb.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.repository.MediaListRepository
import com.example.streamingappzb.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaListUiState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * My List over the real catalogue.
 *
 * The saved keys are the source of truth and the items are resolved from the cache, which
 * is why a saved title still renders with no network: it was cached when it was browsed.
 * A key whose item is no longer cached is skipped rather than shown as a blank tile.
 */
class MediaListViewModel(
    private val mediaList: MediaListRepository,
    private val media: MediaRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(MediaListUiState())
    val state: StateFlow<MediaListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            mediaList.observeKeys().collect { keys ->
                val items = runCatching { media.itemsByKeys(keys) }.getOrDefault(emptyList())
                _state.value = MediaListUiState(items = items, loading = false)
            }
        }
    }

    fun remove(item: MediaItem) {
        viewModelScope.launch {
            val added = mediaList.toggle(item)
            analytics.myListToggle(item.id, added)
        }
    }
}
