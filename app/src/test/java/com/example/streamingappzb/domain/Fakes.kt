package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.OfflinePlayback
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.DownloadRequest
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.domain.repository.NetworkRepository
import com.example.streamingappzb.domain.repository.ProgressRepository
import com.example.streamingappzb.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory repositories for the JVM tests.
 *
 * Each one is backed by a [MutableStateFlow] rather than a plain list, so a test can assert
 * on what a screen does when a repository *emits* — which is where StateFlow conflation
 * bugs live, and is not observable through a suspend-function fake.
 */

class FakeSettingsRepository(
    saver: Boolean = true,
    wifiOnly: Boolean = true,
    smartDownloads: Boolean = false,
) : SettingsRepository {

    override val saver = MutableStateFlow(saver)
    override val wifiOnly = MutableStateFlow(wifiOnly)
    override val smartDownloads = MutableStateFlow(smartDownloads)
    override val subtitles = MutableStateFlow(SubtitleOption.English)
    override val manualQuality = MutableStateFlow<String?>(null)
    override val streamedBytesThisMonth = MutableStateFlow(0L)
    override val wifiBytesThisMonth = MutableStateFlow(0L)

    var seeded = false
        private set
    var askedNotifications = false
        private set

    override fun setSaver(on: Boolean) { saver.value = on }
    override fun setWifiOnly(on: Boolean) { wifiOnly.value = on }
    override fun setSmartDownloads(on: Boolean) { smartDownloads.value = on }
    override fun setSubtitles(option: SubtitleOption) { subtitles.value = option }
    override fun setManualQuality(rungId: String?) { manualQuality.value = rungId }

    override fun addStreamedBytes(bytes: Long, metered: Boolean) {
        if (metered) {
            streamedBytesThisMonth.value += bytes
        } else {
            wifiBytesThisMonth.value += bytes
        }
    }

    override fun isFirstRun(): Boolean = !seeded
    override fun markSeeded() { seeded = true }
    override fun hasAskedNotifications(): Boolean = askedNotifications
    override fun markAskedNotifications() { askedNotifications = true }
}

class FakeNetworkRepository(initial: NetworkState = NetworkState.Wifi) : NetworkRepository {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<NetworkState> = mutable
    override fun current(): NetworkState = mutable.value

    fun set(next: NetworkState) { mutable.value = next }
}

class FakeMyListRepository(initial: List<Int> = emptyList()) : MyListRepository {
    private val ids = MutableStateFlow(initial)

    override fun observe(): Flow<List<Int>> = ids
    override suspend fun ids(): Set<Int> = ids.value.toSet()
    override suspend fun contains(titleId: Int): Boolean = titleId in ids.value

    override suspend fun toggle(titleId: Int): Boolean {
        val present = titleId in ids.value
        // Most recently added first, matching the grid's order.
        ids.value = if (present) ids.value - titleId else listOf(titleId) + ids.value
        return !present
    }
}

class FakeProgressRepository(initial: List<Progress> = emptyList()) : ProgressRepository {
    private val items = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Progress>> = items
    override suspend fun all(): List<Progress> = items.value
    override suspend fun get(titleId: Int): Progress? = items.value.firstOrNull { it.titleId == titleId }

    override suspend fun put(progress: Progress) {
        items.value = listOf(progress) + items.value.filterNot { it.titleId == progress.titleId }
    }

    override suspend fun delete(titleId: Int) {
        items.value = items.value.filterNot { it.titleId == titleId }
    }
}

class FakeDownloadRepository(initial: List<DownloadItem> = emptyList()) : DownloadRepository {
    private val items = MutableStateFlow(initial)

    val enqueued = mutableListOf<DownloadRequest>()
    var wifiOnlyApplied: Boolean? = null
        private set

    override fun observeAll(): Flow<List<DownloadItem>> = items
    override suspend fun all(): List<DownloadItem> = items.value

    override suspend fun get(titleId: Int, episode: Int): DownloadItem? =
        items.value.firstOrNull { it.titleId == titleId && it.episode == episode }

    override suspend fun isDownloaded(titleId: Int, episode: Int): Boolean =
        get(titleId, episode) != null

    override suspend fun offlinePlayback(titleId: Int, episode: Int): OfflinePlayback? = null

    override suspend fun enqueue(requests: List<DownloadRequest>) {
        enqueued += requests
    }

    override suspend fun togglePause(key: String) = Unit
    override suspend fun remove(key: String) {
        items.value = items.value.filterNot { it.key == key }
    }

    override suspend fun promoteWaitingToQueued() = Unit

    override suspend fun applyWifiOnly(wifiOnly: Boolean) {
        wifiOnlyApplied = wifiOnly
    }

    override suspend fun purgeExpired(nowMillis: Long): Int = 0

    fun emit(next: List<DownloadItem>) { items.value = next }
}
