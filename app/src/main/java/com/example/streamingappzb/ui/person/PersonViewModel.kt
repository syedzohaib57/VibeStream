package com.example.streamingappzb.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.domain.model.Credit
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.usecase.GetPersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonUiState(
    val person: PersonProfile? = null,
    val films: List<Credit> = emptyList(),
    val series: List<Credit> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * A person and their filmography, reached by tapping a face on a title screen.
 *
 * One fetch, because TMDB appends the whole filmography to the detail call — the screen
 * either has everything or has failed, so there is no partial state to model.
 */
class PersonViewModel(
    private val personId: Int,
    private val getPerson: GetPersonUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = _state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            val result = runCatching { getPerson(personId) }.getOrNull()
            _state.value = if (result == null) {
                _state.value.copy(loading = false, failed = true)
            } else {
                PersonUiState(
                    person = result.person,
                    films = result.films,
                    series = result.series,
                    loading = false,
                    failed = false,
                )
            }
        }
    }

    fun onOpened(item: MediaItem) = analytics.titleView(item.id)
}
