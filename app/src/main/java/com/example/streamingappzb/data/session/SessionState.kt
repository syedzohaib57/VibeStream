package com.example.streamingappzb.data.session

import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.repository.NetworkRepository
import com.example.streamingappzb.domain.repository.ProgressRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide state (PRD §3), mapping one to one onto the `App()` state in the design's
 * `index.html`: network, saver, quality, continue, myList, downloads.
 *
 * It also owns the two reactive rules that live in `index.html`'s effects, so no screen
 * has to remember them:
 *
 * ```js
 * // FR-204: on mobile data, default to 240p and never exceed the 360p cap while Saver is on.
 * React.useEffect(() => {
 *   if (network === 'wifi') setQuality('720p');
 *   else setQuality(saver ? '240p' : '480p');
 * }, [network, saver]);
 *
 * // Wi-Fi arrived: every `waiting` download becomes `queued`.
 * React.useEffect(() => {
 *   if (network === 'wifi') setDownloads(promoteWaiting);
 * }, [network]);
 * ```
 */
class SessionState(
    private val settings: SettingsRepository,
    network: NetworkRepository,
    progress: ProgressRepository,
    myList: MyListRepository,
    private val downloads: DownloadRepository,
    private val resolveQuality: ResolveQualityUseCase,
    private val scope: CoroutineScope,
) {

    val networkState: StateFlow<NetworkState> = network.state

    val saver: StateFlow<Boolean> = settings.saver

    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly

    val smartDownloads: StateFlow<Boolean> = settings.smartDownloads

    val subtitles: StateFlow<SubtitleOption> = settings.subtitles

    /**
     * The rung in force. Derived, never assigned: the FR-204 rules plus any still-allowed
     * manual pick. Everything that displays "240p · 155 MB/hr" reads this.
     */
    val quality: StateFlow<Rung> = combine(
        network.state,
        settings.saver,
        settings.manualQuality,
    ) { net, saverOn, manual ->
        resolveQuality.resolve(net, saverOn, manual)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = resolveQuality.resolve(
            network.current(),
            settings.saver.value,
            settings.manualQuality.value,
        ),
    )

    val continueWatching: StateFlow<List<Progress>> = progress.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val myListIds: StateFlow<List<Int>> = myList.observe()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val downloadItems: StateFlow<List<DownloadItem>> = downloads.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Drives the app bar's cyan dot and the bottom nav's badge. */
    val activeDownloadCount: StateFlow<Int> = downloadItems
        .map { items -> items.count { it.state.isActive } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        scope.launch {
            networkState.collect { state ->
                // "A manual pick holds for the session until the network changes"
                // (FR-204). This also fires on the first emission, which is correct: a
                // new process is a new session, so a stored pick must not survive it.
                settings.setManualQuality(null)

                // "When the network switches to Wi-Fi, every `waiting` item moves to
                // `queued`" (PRD §6.5).
                if (state == NetworkState.Wifi) {
                    downloads.promoteWaitingToQueued()
                }
            }
        }

        scope.launch {
            // NETWORK_UNMETERED while Wi-Fi-only is on, NETWORK otherwise (PRD §6.5).
            settings.wifiOnly.collect { downloads.applyWifiOnly(it) }
        }
    }

    /** Month-to-date figures behind the data sheet's "This month" line. */
    fun megabytesStreamedThisMonth(): Double =
        settings.streamedBytesThisMonth.value.toDouble() / BYTES_PER_MB

    fun megabytesOnWifiThisMonth(): Double =
        settings.wifiBytesThisMonth.value.toDouble() / BYTES_PER_MB

    fun isInMyList(titleId: Int): Boolean = myListIds.value.contains(titleId)

    fun progressFor(titleId: Int): Progress? =
        continueWatching.value.firstOrNull { it.titleId == titleId }

    fun downloadFor(titleId: Int, episode: Int): DownloadItem? =
        downloadItems.value.firstOrNull { it.titleId == titleId && it.episode == episode }

    fun isDownloaded(titleId: Int, episode: Int): Boolean =
        downloadFor(titleId, episode)?.state == com.example.streamingappzb.domain.model.DlState.Done

    private companion object {
        /** MB as 10^6 bytes, the unit carriers bill in — and the one the §6.7 ladder uses. */
        const val BYTES_PER_MB = 1_000_000.0
    }
}
