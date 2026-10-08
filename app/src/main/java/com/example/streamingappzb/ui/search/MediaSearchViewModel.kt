package com.example.streamingappzb.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.GenreScope
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.usecase.GetBrowseResultsUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class MediaSearchUiState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val filter: MediaType? = null,
    /** TMDB's genre list, for the browse grid shown before anything is typed. */
    val genres: List<MediaGenre> = emptyList(),
    val searching: Boolean = false,
) {
    val visible: List<MediaItem>
        get() = filter?.let { type -> results.filter { it.type == type } } ?: results

    val showEmptyState: Boolean
        get() = query.isNotBlank() && !searching && visible.isEmpty()

    val showSuggestions: Boolean get() = query.isBlank()
}

/**
 * Search across films, series and anime.
 *
 * Runs against TMDB and AniList together and falls back to the local cache when both are
 * unreachable — so a search made on a train still returns the things already browsed,
 * rather than an empty screen.
 *
 * The genre list is loaded up front rather than on demand: it is what the tab shows before
 * the viewer types, so fetching it when they stop typing would be too late to be useful.
 */
@OptIn(FlowPreview::class)
class MediaSearchViewModel(
    private val media: MediaRepository,
    private val browse: GetBrowseResultsUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(MediaSearchUiState())
    val state: StateFlow<MediaSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            query
                // Short enough that results feel live, long enough that a fast typist does
                // not spend a network call per keystroke — these are metered requests.
                .debounce(DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect(::run)
        }
        loadGenres()
    }

    /**
     * Film genres only. Showing both spaces means Drama twice, and the film list is the
     * superset a viewer expects from a generic browse grid — a series-specific genre is
     * reachable from the Series tab's own rows.
     */
    private fun loadGenres() {
        viewModelScope.launch {
            val fetched = runCatching { browse.genres(GenreScope.Movie) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(genres = fetched)
        }
    }

    fun onQueryChanged(text: String) {
        query.value = text
        // Reflect the typing immediately; results follow after the debounce.
        _state.value = _state.value.copy(query = text, searching = text.isNotBlank())
        if (text.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false)
        }
    }

    fun setFilter(type: MediaType?) {
        _state.value = _state.value.copy(filter = type)
    }

    fun clear() = onQueryChanged("")

    private suspend fun run(text: String) {
        if (text.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false)
            return
        }
        val results = runCatching { media.search(text) }.getOrDefault(emptyList())
        _state.value = _state.value.copy(query = text, results = results, searching = false)
        analytics.search(text.length, results.size)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
