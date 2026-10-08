package com.example.streamingappzb.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.media.EpisodeRef
import com.example.streamingappzb.data.media.FreeSourceRepository
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.EpisodeSummary
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.MovieCollection
import com.example.streamingappzb.domain.model.WatchOffer
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.repository.MediaListRepository
import com.example.streamingappzb.domain.usecase.GetWatchOptionsUseCase
import com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MediaDetailUiState(
    val detail: MediaDetail? = null,
    val primary: ResolvePlaybackUseCase.Action? = null,
    val secondary: ResolvePlaybackUseCase.Action? = null,
    val inMyList: Boolean = false,
    val selectedSeason: Int = 1,
    val episodes: List<EpisodeSummary> = emptyList(),
    /**
     * The film series this title belongs to. A second call, so it lands after the screen is
     * already drawn — null until then, and for the overwhelming majority of titles forever.
     */
    val collection: MovieCollection? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val isSeries: Boolean get() = detail?.type?.isSeries == true
}

/**
 * The title screen.
 *
 * Its job is to answer "can I watch this, and where" — so the playback action is resolved
 * once, up front, by [ResolvePlaybackUseCase], and the screen simply renders whatever that
 * decided. The free-source lookup runs alongside the detail fetch rather than after it,
 * because it is the slower of the two and would otherwise delay the whole screen.
 */
class MediaDetailViewModel(
    private val mediaId: Int,
    private val mediaType: MediaType,
    private val media: MediaRepository,
    private val freeSources: FreeSourceRepository,
    private val myList: MediaListRepository,
    private val resolvePlayback: ResolvePlaybackUseCase,
    private val watchOptions: GetWatchOptionsUseCase,
    private val session: SessionState,
    private val analytics: Analytics,
) : ViewModel() {

    private val detail = MutableStateFlow<MediaDetail?>(null)
    private val loading = MutableStateFlow(true)
    private val failed = MutableStateFlow(false)
    private val season = MutableStateFlow(1)
    private val episodes = MutableStateFlow<List<EpisodeSummary>>(emptyList())
    private val collection = MutableStateFlow<MovieCollection?>(null)

    val state: StateFlow<MediaDetailUiState> = combine(
        detail,
        combine(loading, failed) { l, f -> l to f },
        myList.observeKeys(),
        combine(season, episodes) { s, e -> s to e },
        collection,
    ) { currentDetail, (isLoading, didFail), savedKeys, (currentSeason, currentEpisodes), series ->
        val primary = currentDetail?.let(resolvePlayback::invoke)
        MediaDetailUiState(
            detail = currentDetail,
            primary = primary,
            secondary = currentDetail?.let { d ->
                primary?.let { resolvePlayback.secondary(d, it) }
            },
            inMyList = currentDetail?.item?.key?.let(savedKeys::contains) == true,
            selectedSeason = currentSeason,
            episodes = currentEpisodes,
            collection = series,
            loading = isLoading,
            failed = didFail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MediaDetailUiState())

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        analytics.titleView(mediaId)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            loading.value = true
            val fetched = runCatching { media.detail(mediaId, mediaType) }.getOrNull()
            if (fetched == null) {
                failed.value = true
                loading.value = false
                return@launch
            }
            detail.value = fetched
            loading.value = false

            // First season up front, so a series screen is never an empty episode list.
            if (fetched.type.isSeries) {
                val first = fetched.seasons.firstOrNull()?.seasonNumber ?: 1
                season.value = first
                loadEpisodes(first)
            }

            // The collection is a second call and only ever *adds* a row, so it lands on
            // its own rather than holding the screen for a film that is not part of one.
            fetched.collectionId?.let { loadCollection(it) }

            // Slower, and only ever adds a capability, so it lands separately too.
            val source = runCatching { freeSources.sourceFor(fetched.item) }.getOrNull()
            if (source != null) detail.value = detail.value?.copy(playable = source)
        }
    }

    private fun loadCollection(collectionId: Int) {
        viewModelScope.launch {
            collection.value = runCatching { media.collection(collectionId) }.getOrNull()
        }
    }

    fun selectSeason(seasonNumber: Int) {
        if (season.value == seasonNumber) return
        season.value = seasonNumber
        loadEpisodes(seasonNumber)
    }

    private fun loadEpisodes(seasonNumber: Int) {
        viewModelScope.launch {
            episodes.value = runCatching { media.episodes(mediaId, mediaType, seasonNumber) }
                .getOrDefault(emptyList())
        }
    }

    fun toggleMyList() {
        val item = detail.value?.item ?: return
        viewModelScope.launch {
            val added = myList.toggle(item)
            analytics.myListToggle(mediaId, added)
            _events.emit(
                Event.Toast(
                    if (added) {
                        com.example.streamingappzb.R.string.toast_added_to_list
                    } else {
                        com.example.streamingappzb.R.string.toast_removed_from_list
                    },
                ),
            )
        }
    }

    /**
     * Resolves a provider chip to a destination. [isInstalled] is passed in from the
     * Activity because only it can ask the PackageManager.
     */
    fun openProvider(offer: WatchOffer, isInstalled: (String) -> Boolean) {
        val current = detail.value ?: return
        val handoff = watchOptions(
            offer = offer,
            title = current.item.title,
            justWatchLink = current.watch.justWatchLink,
            isInstalled = isInstalled,
        )
        viewModelScope.launch { _events.emit(Event.OpenHandoff(handoff)) }
    }

    fun region(): String = watchOptions.currentRegion()

    fun retry() {
        failed.value = false
        load()
    }

    /**
     * A tapped episode: resolve a source for exactly that episode and play it.
     *
     * Resolution happens here, on tap, rather than up front for the whole list — a season
     * is twenty lookups, and the viewer plays at most one of them. The one they do play
     * costs a moment, so the row's spinner state is driven by [resolvingEpisode].
     */
    fun playEpisode(episode: EpisodeSummary) {
        val item = detail.value?.item ?: return
        if (resolvingEpisode.value != null) return

        viewModelScope.launch {
            resolvingEpisode.value = episode.episodeNumber
            val source = runCatching {
                freeSources.sourceFor(
                    item,
                    EpisodeRef(season = episode.seasonNumber, episode = episode.episodeNumber),
                )
            }.getOrNull()
            resolvingEpisode.value = null

            if (source != null) {
                _events.emit(Event.PlaySource(source))
            } else {
                _events.emit(Event.Toast(R.string.episode_no_source))
            }
        }
    }

    /** Episode number currently resolving, for the tapped row's pending state. */
    val resolvingEpisode = MutableStateFlow<Int?>(null)

    sealed interface Event {
        data class Toast(val messageRes: Int) : Event

        data class OpenHandoff(val handoff: GetWatchOptionsUseCase.Handoff) : Event

        /** A per-episode source resolved; the Activity routes it to the player. */
        data class PlaySource(val source: com.example.streamingappzb.domain.model.PlayableSource) : Event
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
