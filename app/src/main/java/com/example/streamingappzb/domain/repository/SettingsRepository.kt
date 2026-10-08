package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.SubtitleOption
import kotlinx.coroutines.flow.StateFlow

/**
 * Viewer settings, in SharedPreferences.
 *
 * Defaults matter here and are specified: Data Saver **on** and Wi-Fi-only downloads
 * **on** (PRD §6.7 / §6.5), Smart downloads **off**.
 */
interface SettingsRepository {

    /** Data Saver. On by default — this app is built for metered data. */
    val saver: StateFlow<Boolean>

    /** Download on Wi-Fi only. On by default. */
    val wifiOnly: StateFlow<Boolean>

    /** Fetch the next episode on Wi-Fi and delete the watched one. Off by default. */
    val smartDownloads: StateFlow<Boolean>

    val subtitles: StateFlow<SubtitleOption>

    /**
     * A rung the viewer picked by hand. It holds for the session until the network
     * changes, at which point the FR-204 defaults re-apply and this is cleared.
     */
    val manualQuality: StateFlow<String?>

    fun setSaver(on: Boolean)

    fun setWifiOnly(on: Boolean)

    fun setSmartDownloads(on: Boolean)

    fun setSubtitles(option: SubtitleOption)

    fun setManualQuality(rungId: String?)

    /** Month-to-date counters behind the Data sheet's "This month" line. */
    val streamedBytesThisMonth: StateFlow<Long>
    val wifiBytesThisMonth: StateFlow<Long>

    fun addStreamedBytes(bytes: Long, metered: Boolean)

    /** True the very first time the app runs, used to seed the catalogue. */
    fun isFirstRun(): Boolean

    fun markSeeded()

    /** Whether the notification rationale has already been shown, so it is asked once. */
    fun hasAskedNotifications(): Boolean

    fun markAskedNotifications()
}
