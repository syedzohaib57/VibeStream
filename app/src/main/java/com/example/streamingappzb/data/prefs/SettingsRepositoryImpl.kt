package com.example.streamingappzb.data.prefs

import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SettingsRepository] over [AppPrefs].
 *
 * Every setting is mirrored into a StateFlow so screens observe rather than poll — which
 * is what lets the app-bar pill and the player's data note react to a Saver toggle
 * within a frame (acceptance item 6).
 */
class SettingsRepositoryImpl(private val prefs: AppPrefs) : SettingsRepository {

    private val _saver = MutableStateFlow(prefs.saver)
    override val saver: StateFlow<Boolean> = _saver.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.wifiOnly)
    override val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _smart = MutableStateFlow(prefs.smartDownloads)
    override val smartDownloads: StateFlow<Boolean> = _smart.asStateFlow()

    private val _subtitles = MutableStateFlow(SubtitleOption.from(prefs.subtitles))
    override val subtitles: StateFlow<SubtitleOption> = _subtitles.asStateFlow()

    private val _manualQuality = MutableStateFlow(prefs.manualQuality)
    override val manualQuality: StateFlow<String?> = _manualQuality.asStateFlow()

    private val _streamed = MutableStateFlow(prefs.streamedBytes)
    override val streamedBytesThisMonth: StateFlow<Long> = _streamed.asStateFlow()

    private val _wifiBytes = MutableStateFlow(prefs.wifiBytes)
    override val wifiBytesThisMonth: StateFlow<Long> = _wifiBytes.asStateFlow()

    override fun setSaver(on: Boolean) {
        if (prefs.saver == on) return
        prefs.saver = on
        // Toggling Saver re-applies the FR-204 defaults, so a manual pick stops holding.
        setManualQuality(null)
        _saver.value = on
    }

    override fun setWifiOnly(on: Boolean) {
        prefs.wifiOnly = on
        _wifiOnly.value = on
    }

    override fun setSmartDownloads(on: Boolean) {
        prefs.smartDownloads = on
        _smart.value = on
    }

    override fun setSubtitles(option: SubtitleOption) {
        prefs.subtitles = option.name
        _subtitles.value = option
    }

    override fun setManualQuality(rungId: String?) {
        prefs.manualQuality = rungId
        _manualQuality.value = rungId
    }

    override fun addStreamedBytes(bytes: Long, metered: Boolean) {
        prefs.addBytes(bytes, metered)
        _streamed.value = prefs.streamedBytes
        _wifiBytes.value = prefs.wifiBytes
    }

    override fun isFirstRun(): Boolean = !prefs.isSeeded()

    override fun markSeeded() = prefs.markSeeded()

    override fun hasAskedNotifications(): Boolean = prefs.hasAskedNotifications()

    override fun markAskedNotifications() = prefs.markAskedNotifications()
}
