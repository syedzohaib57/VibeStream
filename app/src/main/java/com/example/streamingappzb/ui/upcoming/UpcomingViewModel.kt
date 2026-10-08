package com.example.streamingappzb.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.media.Clock
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A month heading and the releases under it. */
data class UpcomingGroup(
    val label: String,
    val items: List<MediaItem>,
)

data class UpcomingUiState(
    val groups: List<UpcomingGroup> = emptyList(),
    val filter: MediaType? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

/**
 * What's coming — films, new seasons and anime, soonest first.
 *
 * Grouped by month rather than shown as one long list: a release schedule is something
 * people read by month, and 60 undifferentiated posters answer "what's out soon" far worse
 * than four labelled groups.
 */
class UpcomingViewModel(
    private val media: MediaRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(UpcomingUiState())
    val state: StateFlow<UpcomingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setFilter(type: MediaType?) {
        if (_state.value.filter == type) return
        _state.value = _state.value.copy(filter = type, loading = true)
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            val filter = _state.value.filter
            runCatching { media.upcoming(filter) }
                .onSuccess { items ->
                    _state.value = _state.value.copy(
                        groups = group(items),
                        loading = false,
                        failed = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, failed = true)
                }
        }
    }

    /**
     * Groups by `yyyy-MM`, in date order. Anything without a usable date lands in a final
     * "date to be announced" group rather than being dropped — an unannounced date is
     * itself information, and dropping the title would hide a real release.
     */
    private fun group(items: List<MediaItem>): List<UpcomingGroup> {
        val dated = items.filter { (it.releaseDate?.length ?: 0) >= MONTH_KEY_LENGTH }
        val undated = items - dated.toSet()

        val groups = dated
            .groupBy { it.releaseDate!!.take(MONTH_KEY_LENGTH) }
            .toSortedMap()
            .map { (monthKey, monthItems) ->
                UpcomingGroup(
                    label = monthLabel(monthKey),
                    items = monthItems.sortedBy { it.releaseDate },
                )
            }

        return if (undated.isEmpty()) {
            groups
        } else {
            groups + UpcomingGroup(UNDATED_LABEL, undated)
        }
    }

    private fun monthLabel(monthKey: String): String {
        val year = monthKey.take(4).toIntOrNull() ?: return monthKey
        val month = monthKey.drop(5).toIntOrNull() ?: return monthKey
        val name = MONTHS.getOrNull(month - 1) ?: return monthKey
        // The year is redundant for the current one and noise on every heading.
        return if (year == clock.year()) name else "$name $year"
    }

    private companion object {
        const val MONTH_KEY_LENGTH = 7
        const val UNDATED_LABEL = "Date to be announced"

        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}
