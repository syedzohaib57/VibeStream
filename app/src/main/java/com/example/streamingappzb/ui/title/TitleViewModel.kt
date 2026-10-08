package com.example.streamingappzb.ui.title

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.usecase.DownloadSizeOption
import com.example.streamingappzb.domain.usecase.EnqueueDownloadUseCase
import com.example.streamingappzb.domain.usecase.EstimateDownloadSizeUseCase
import com.example.streamingappzb.domain.usecase.GetTitleDetailUseCase
import com.example.streamingappzb.domain.usecase.TitleScreenData
import com.example.streamingappzb.domain.model.Rung
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TitleUiState(
    val data: TitleScreenData? = null,
    val network: NetworkState = NetworkState.Cellular,
    val wifiOnly: Boolean = true,
)

/** What the download sheet needs to render. */
data class DownloadSheetState(
    /** null means the whole season. */
    val episode: Int?,
    val options: List<DownloadSizeOption>,
    val selectedRungId: String,
    /** Wi-Fi-only on a metered connection: offer Queue for Wi-Fi rather than Download. */
    val queueForWifi: Boolean,
    val titleName: String,
    val isFilm: Boolean,
)

class TitleViewModel(
    private val titleId: Int,
    private val getTitleDetail: GetTitleDetailUseCase,
    private val estimateSize: EstimateDownloadSizeUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
    private val myList: MyListRepository,
    private val downloads: DownloadRepository,
    private val session: SessionState,
    private val analytics: Analytics,
) : ViewModel() {

    private val data = MutableStateFlow<TitleScreenData?>(null)

    val state: StateFlow<TitleUiState> = combine(
        data,
        session.networkState,
        session.wifiOnly,
    ) { screenData, network, wifiOnly ->
        TitleUiState(data = screenData, network = network, wifiOnly = wifiOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TitleUiState())

    private val _sheet = MutableStateFlow<DownloadSheetState?>(null)
    val sheet: StateFlow<DownloadSheetState?> = _sheet.asStateFlow()

    private val _messages = MutableSharedFlow<TitleMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TitleMessage> = _messages.asSharedFlow()

    init {
        analytics.titleView(titleId)
        refresh()
        // Progress and download state both change while this screen is open — finishing an
        // episode in the player, a download completing — so the list stays live.
        viewModelScope.launch {
            combine(session.continueWatching, session.downloadItems, session.myListIds) { _, _, _ -> Unit }
                .collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch { data.value = getTitleDetail(titleId) }
    }

    fun toggleMyList() {
        viewModelScope.launch {
            val added = myList.toggle(titleId)
            analytics.myListToggle(titleId, added)
            emit(
                TitleMessage.Toast(
                    if (added) R.string.toast_added_to_list else R.string.toast_removed_from_list,
                ),
            )
            refresh()
        }
    }

    // ---------------------------------------------------------------- downloads

    /** @param episode null for "Download season". */
    fun openDownloadSheet(episode: Int?) {
        val screenData = data.value ?: return
        if (!screenData.detail.title.downloadable) {
            // A stream-only title never opens the sheet and never enqueues
            // (acceptance item 11).
            emit(TitleMessage.Toast(R.string.stream_only_toast))
            return
        }
        viewModelScope.launch {
            val rungId = _sheet.value?.selectedRungId ?: Rung.DEFAULT_DOWNLOAD
            _sheet.value = buildSheet(episode, rungId)
        }
    }

    fun selectRung(rungId: String) {
        val current = _sheet.value ?: return
        viewModelScope.launch { _sheet.value = buildSheet(current.episode, rungId) }
    }

    fun dismissSheet() {
        _sheet.value = null
    }

    fun confirmDownload(useMobileDataNow: Boolean) {
        val current = _sheet.value ?: return
        val screenData = data.value ?: return
        val episodes = current.episode?.let { listOf(it) }
            ?: screenData.detail.episodes.map { it.number }

        viewModelScope.launch {
            val result = enqueueDownload(
                titleId = titleId,
                episodes = episodes,
                rungId = current.selectedRungId,
                useMobileDataNow = useMobileDataNow,
            )
            _sheet.value = null
            emit(result.toMessage(current))
            refresh()
        }
    }

    /**
     * The trailing glyph on an episode row. Its behaviour depends on where the episode
     * already is: nothing yet opens the sheet, anything in flight goes to Downloads, and a
     * stream-only title only ever explains itself.
     */
    fun onEpisodeDownloadGlyph(episode: Int) {
        val screenData = data.value ?: return
        if (!screenData.detail.title.downloadable) {
            emit(TitleMessage.Toast(R.string.stream_only_toast))
            return
        }
        val existing = screenData.downloadsByEpisode[episode]
        if (existing == null) {
            openDownloadSheet(episode)
        } else {
            emit(TitleMessage.OpenDownloads)
        }
    }

    fun onPlay(episode: Int, positionSeconds: Int) {
        emit(TitleMessage.Play(episode, positionSeconds))
    }

    fun onShare() {
        val screenData = data.value ?: return
        emit(TitleMessage.Share(screenData))
    }

    // ---------------------------------------------------------------- internals

    private suspend fun buildSheet(episode: Int?, rungId: String): DownloadSheetState? {
        val screenData = data.value ?: return null
        return DownloadSheetState(
            episode = episode,
            options = estimateSize.options(titleId, episode, rungId),
            selectedRungId = rungId,
            queueForWifi = session.networkState.value.isMetered && session.wifiOnly.value,
            titleName = screenData.detail.title.title,
            isFilm = screenData.detail.title.isFilm,
        )
    }

    private fun EnqueueDownloadUseCase.Result.toMessage(
        sheet: DownloadSheetState,
    ): TitleMessage = when (this) {
        EnqueueDownloadUseCase.Result.StreamOnly ->
            TitleMessage.Toast(R.string.stream_only_toast)

        EnqueueDownloadUseCase.Result.AlreadyPresent ->
            TitleMessage.OpenDownloads

        is EnqueueDownloadUseCase.Result.Enqueued -> when {
            waitingForWifi -> TitleMessage.Toast(R.string.toast_queued_for_wifi)
            episodeCount > 1 -> TitleMessage.ToastPlural(
                R.plurals.downloading_episodes,
                episodeCount,
            )

            sheet.isFilm -> TitleMessage.ToastFormatted(
                R.string.toast_downloading_film,
                sheet.titleName,
            )

            // Passed as an Int, not a String: toast_downloading_episode is a %d.
            else -> TitleMessage.ToastFormatted(
                R.string.toast_downloading_episode,
                firstEpisode,
            )
        }
    }

    private fun emit(message: TitleMessage) {
        viewModelScope.launch { _messages.emit(message) }
    }

    /** One-shot effects the Activity performs. */
    sealed interface TitleMessage {
        data class Toast(@get:StringRes val messageRes: Int) : TitleMessage

        /**
         * [arg] is deliberately `Any` rather than `String`: the messages it feeds mix
         * `%s` (a title) and `%d` (an episode number), and stringifying an Int to fit a
         * `String` field makes `getString` throw IllegalFormatConversionException.
         */
        data class ToastFormatted(@get:StringRes val messageRes: Int, val arg: Any) : TitleMessage

        data class ToastPlural(val pluralRes: Int, val count: Int) : TitleMessage

        data class Play(val episode: Int, val positionSeconds: Int) : TitleMessage

        data class Share(val data: TitleScreenData) : TitleMessage

        data object OpenDownloads : TitleMessage
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Convenience for the episode rows' download state lookup. */
fun TitleScreenData.downloadFor(episode: Int): DownloadItem? = downloadsByEpisode[episode]
