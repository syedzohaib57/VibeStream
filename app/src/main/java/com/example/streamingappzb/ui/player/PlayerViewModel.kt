package com.example.streamingappzb.ui.player

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.player.PlaybackController
import com.example.streamingappzb.data.player.PlaybackState
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.QualityOption
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import com.example.streamingappzb.domain.usecase.SaveProgressUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val rung: Rung = Rung.LADDER[1],
    val subtitles: SubtitleOption = SubtitleOption.English,
    val network: NetworkState = NetworkState.Cellular,
    val saver: Boolean = true,
) {
    /** The data note under the controls (PRD §6.3). */
    @get:StringRes
    val dataNoteRes: Int
        get() = when {
            playback.playingFromDownload -> R.string.data_note_downloaded
            network == NetworkState.Wifi -> R.string.data_note_wifi
            network == NetworkState.Offline -> R.string.data_note_downloaded
            saver -> R.string.data_note_saver_on
            else -> R.string.data_note_saver_off
        }

    /** Cyan while Data Saver is actually saving something, textLow otherwise. */
    val dataNoteIsCyan: Boolean get() = network.isMetered && saver
}

class PlayerViewModel(
    private val args: PlayerArgs,
    val controller: PlaybackController,
    private val catalog: CatalogRepository,
    private val downloads: DownloadRepository,
    private val saveProgress: SaveProgressUseCase,
    private val resolveQuality: ResolveQualityUseCase,
    private val settings: SettingsRepository,
    private val session: SessionState,
    private val analytics: Analytics,
) : ViewModel() {

    private val playback = MutableStateFlow(PlaybackState())

    val state: StateFlow<PlayerUiState> = combine(
        playback,
        session.quality,
        session.subtitles,
        session.networkState,
        session.saver,
    ) { playbackState, rung, subs, network, saver ->
        PlayerUiState(
            playback = playbackState,
            rung = rung,
            subtitles = subs,
            network = network,
            saver = saver,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    /**
     * Null for a free source, which has no catalogue row.
     *
     * Everything keyed by it — progress, downloads, the ad schedule, analytics — is skipped
     * rather than given a stand-in id. The progress table is keyed by catalogue id, so a
     * TMDB id written into it would collide with a real one.
     */
    private val titleId: Int? = (args as? PlayerArgs.Catalog)?.titleId

    private var currentEpisode = (args as? PlayerArgs.Catalog)?.episode ?: 0

    init {
        controller.onStateChanged = { playback.value = it }
        controller.onSaveProgress = { position -> persist(position) }
        controller.onEpisodeFinished = { onEpisodeFinished() }
        controller.onAutoAdvance = { playNext() }
        controller.onBytesTransferred = { bytes ->
            // Split by transport, which is what the data sheet reports separately.
            settings.addStreamedBytes(bytes, metered = session.networkState.value.isMetered)
        }

        when (args) {
            is PlayerArgs.Catalog -> load(args.episode, args.positionSeconds)
            is PlayerArgs.Source -> controller.loadSource(
                source = args.source,
                startPositionSeconds = args.positionSeconds,
                rung = session.quality.value,
                subtitles = session.subtitles.value,
            )
        }
        startClock()
        observeQuality()
    }

    // ---------------------------------------------------------------- loading

    private fun load(episodeNumber: Int, positionSeconds: Int) {
        val titleId = titleId ?: return
        viewModelScope.launch {
            val detail = catalog.titleDetail(titleId) ?: run {
                emit(PlayerEvent.Fatal)
                return@launch
            }
            val episode = detail.episode(episodeNumber) ?: detail.episodes.firstOrNull() ?: run {
                emit(PlayerEvent.Fatal)
                return@launch
            }
            currentEpisode = episode.number

            // Non-null means the episode is downloaded, which decides both the media source
            // and whether there are ads at all.
            val offline = downloads.offlinePlayback(titleId, episode.number)
            // A downloaded episode was monetised at download time, so no ads at all
            // (FR-505 / the design's `ad: dl ? 0 : 15`).
            val schedule = catalog.adSchedule(titleId, adFree = offline != null)

            controller.load(
                title = detail.title,
                episode = episode,
                nextEpisode = detail.next(episode.number),
                startPositionSeconds = positionSeconds,
                schedule = schedule,
                rung = session.quality.value,
                subtitles = session.subtitles.value,
                offline = offline,
            )

            analytics.playStart(
                titleId = titleId,
                episode = episode.number,
                rung = session.quality.value.id,
                network = session.networkState.value.name,
            )
        }
    }

    /**
     * The tick. Runs only while something is playing, which is what stops the Up next
     * countdown when the viewer pauses.
     */
    private fun startClock() {
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                if (controller.state.playing || controller.state.inAd) {
                    controller.tick(TICK_MILLIS)
                }
            }
        }
    }

    /**
     * Re-applies the rung whenever it changes — a network switch, a Saver toggle, or a
     * manual pick. Applied to the live player, never by restarting the stream (FR-204).
     */
    private fun observeQuality() {
        viewModelScope.launch {
            var previous: String? = null
            session.quality.collect { rung ->
                controller.applyRung(rung)
                if (previous != null && previous != rung.id) {
                    analytics.qualityChange(
                        from = previous!!,
                        to = rung.id,
                        network = session.networkState.value.name,
                        saver = session.saver.value,
                    )
                }
                previous = rung.id
            }
        }
        viewModelScope.launch {
            session.subtitles.collect { controller.applySubtitles(it) }
        }
    }

    // ---------------------------------------------------------------- commands

    fun togglePlayPause() = controller.togglePlayPause()

    fun seekTo(seconds: Int) = controller.seekTo(seconds)

    fun replay10() = controller.skipBy(-SKIP_SECONDS)

    fun forward10() = controller.skipBy(SKIP_SECONDS)

    fun skipAd() = controller.skipAd()

    fun showUpNext() = controller.startUpNext()

    fun cancelUpNext() = controller.cancelUpNext()

    fun playNext() {
        val next = controller.state.nextEpisode ?: return
        controller.flushProgress()
        load(next.number, 0)
    }

    fun setQuality(rungId: String) {
        val rung = catalog.rung(rungId)
        if (resolveQuality.isLocked(rung, session.networkState.value, session.saver.value)) {
            emit(PlayerEvent.Toast(R.string.toast_saver_locked))
            return
        }
        settings.setManualQuality(rungId)
    }

    fun qualityOptions(): List<QualityOption> = resolveQuality.options(
        network = session.networkState.value,
        saver = session.saver.value,
        currentRungId = session.quality.value.id,
    )

    fun toggleSaver() {
        val next = !session.saver.value
        settings.setSaver(next)
        analytics.saverToggle(next)
    }

    fun setSubtitles(option: SubtitleOption) = settings.setSubtitles(option)

    /** Back saves progress **before** the screen pops (PRD §4). */
    fun onBackPressed() {
        controller.flushProgress()
    }

    fun onStopped() {
        controller.flushProgress()
    }

    // ---------------------------------------------------------------- progress

    private fun persist(positionSeconds: Int) {
        // Nothing to write against for a free source; resume is a catalogue feature.
        val titleId = titleId ?: return
        val duration = controller.state.durationSeconds.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            saveProgress(
                titleId = titleId,
                episode = currentEpisode,
                positionSeconds = positionSeconds,
                durationSeconds = duration,
                nowMillis = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Watched through. Advances to the next episode, or leaves the player when there is
     * nothing to advance to (PRD §6.3).
     */
    private fun onEpisodeFinished() {
        // A free source is a single film: nothing to advance to, no progress to write.
        val titleId = titleId ?: run {
            emit(PlayerEvent.Finish)
            return
        }
        val finished = currentEpisode
        val duration = controller.state.durationSeconds
        viewModelScope.launch {
            saveProgress(
                titleId = titleId,
                episode = finished,
                positionSeconds = duration,
                durationSeconds = duration,
                nowMillis = System.currentTimeMillis(),
            )
            // Smart downloads: fetch the next episode on Wi-Fi and drop the watched one.
            if (settings.smartDownloads.value) {
                emit(PlayerEvent.ScheduleSmartDownload(titleId, finished))
            }
            if (controller.state.nextEpisode != null) playNext() else emit(PlayerEvent.Finish)
        }
    }

    private fun emit(event: PlayerEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    override fun onCleared() {
        controller.onStateChanged = null
        controller.release()
    }

    sealed interface PlayerEvent {
        data class Toast(@get:StringRes val messageRes: Int) : PlayerEvent

        data class ScheduleSmartDownload(val titleId: Int, val finishedEpisode: Int) : PlayerEvent

        /** Nothing left to play. */
        data object Finish : PlayerEvent

        /** The title or episode could not be resolved at all. */
        data object Fatal : PlayerEvent
    }

    private companion object {
        /** 4 Hz: smooth enough for the seek rail, cheap enough to run continuously. */
        const val TICK_MILLIS = 250L
        const val SKIP_SECONDS = 10
    }
}
