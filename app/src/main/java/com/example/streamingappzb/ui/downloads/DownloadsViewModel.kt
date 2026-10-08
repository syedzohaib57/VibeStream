package com.example.streamingappzb.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadRow
import com.example.streamingappzb.domain.model.DownloadsView
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val catalog: CatalogRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DownloadsView(
            inProgress = emptyList(),
            ready = emptyList(),
            wifiOnly = true,
            smartDownloads = false,
            network = NetworkState.Cellular,
        ),
    )
    val state: StateFlow<DownloadsView> = _state.asStateFlow()

    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when a download finishes while this screen is open. */
    val completed: SharedFlow<Unit> = _completed.asSharedFlow()

    private var lastReadyCount = -1

    init {
        viewModelScope.launch {
            catalog.ensureSeeded()
            combine(
                session.downloadItems,
                session.wifiOnly,
                session.smartDownloads,
                session.networkState,
            ) { items, wifiOnly, smart, network ->
                val rows = items.mapNotNull { item ->
                    val detail = catalog.titleDetail(item.titleId) ?: return@mapNotNull null
                    val episode = detail.episode(item.episode) ?: return@mapNotNull null
                    DownloadRow(item = item, title = detail.title, episode = episode)
                }
                DownloadsView(
                    // The PRD's two sections: anything unfinished, then anything ready.
                    inProgress = rows.filter { it.item.state != DlState.Done },
                    ready = rows.filter { it.item.state == DlState.Done },
                    wifiOnly = wifiOnly,
                    smartDownloads = smart,
                    network = network,
                )
            }.collect { view ->
                // Skip the first emission: arriving on a screen that already has finished
                // downloads is not a completion.
                if (lastReadyCount in 0 until view.ready.size) _completed.tryEmit(Unit)
                lastReadyCount = view.ready.size
                _state.value = view
            }
        }
    }

    fun setWifiOnly(on: Boolean) {
        settings.setWifiOnly(on)
    }

    /** @return the toast to show, since the setting explains what it will now do. */
    fun setSmartDownloads(on: Boolean): Int {
        settings.setSmartDownloads(on)
        return if (on) {
            com.example.streamingappzb.R.string.toast_smart_on
        } else {
            com.example.streamingappzb.R.string.toast_smart_off
        }
    }

    /** The trailing button: pause a running item, resume a paused one. */
    fun togglePause(key: String) {
        viewModelScope.launch { downloads.togglePause(key) }
    }

    fun remove(key: String) {
        viewModelScope.launch { downloads.remove(key) }
    }
}
