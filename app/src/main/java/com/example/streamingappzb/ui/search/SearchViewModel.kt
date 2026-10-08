package com.example.streamingappzb.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.repository.CatalogRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Title> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    val showGenres: Boolean get() = query.isBlank()
    val showEmptyState: Boolean get() = query.isNotBlank() && results.isEmpty()
}

/**
 * Search (PRD §6.4). Matching is [com.example.streamingappzb.domain.search.FuzzyMatcher],
 * which runs locally so search works offline; the same rules run server-side in production
 * (PRD §7).
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val catalog: CatalogRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.ensureSeeded()
            _state.value = _state.value.copy(genres = catalog.genres())
        }

        viewModelScope.launch {
            // A short debounce so a fast typist does not trigger a match per keystroke,
            // short enough that results still feel live.
            query
                .debounce(DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { text ->
                    val results = if (text.isBlank()) emptyList() else catalog.search(text)
                    _state.value = _state.value.copy(query = text, results = results)
                    if (text.isNotBlank()) analytics.search(text.length, results.size)
                }
        }
    }

    fun setQuery(text: String) {
        // Echoed immediately so the clear button and the hint react without waiting for
        // the debounce.
        _state.value = _state.value.copy(query = text)
        query.value = text
    }

    fun clear() = setQuery("")

    private companion object {
        const val DEBOUNCE_MILLIS = 150L
    }
}
