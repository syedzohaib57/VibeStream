package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.OfflinePlayback
import kotlinx.coroutines.flow.Flow

/**
 * The offline library (PRD §6.5). Backed by Media3's DownloadManager, with our own Room
 * mirror carrying the two things Media3 does not model: the rung the viewer picked with
 * its MB estimate (FR-302), and the explicit `waiting` state from "Queue for Wi-Fi"
 * (FR-303).
 */
interface DownloadRepository {

    fun observeAll(): Flow<List<DownloadItem>>

    suspend fun all(): List<DownloadItem>

    suspend fun get(titleId: Int, episode: Int): DownloadItem?

    suspend fun isDownloaded(titleId: Int, episode: Int): Boolean

    /**
     * What a completed download holds, for playing it back with no network.
     *
     * Null when the episode is not downloaded, in which case the player streams from the
     * title's own URL instead.
     */
    suspend fun offlinePlayback(titleId: Int, episode: Int): OfflinePlayback?

    /**
     * Enqueue one or more episodes. Already-present keys are left alone, matching the
     * design's `if (!n[k])` guard — asking twice never duplicates or restarts.
     *
     * Callers must have checked the rights record first: a stream-only title never
     * reaches here (PRD §6.2, acceptance item 11).
     */
    suspend fun enqueue(requests: List<DownloadRequest>)

    /** The trailing 44dp button: pause a running item, resume a paused one. */
    suspend fun togglePause(key: String)

    suspend fun remove(key: String)

    /** Called when the network turns unmetered: every `waiting` item becomes `queued`. */
    suspend fun promoteWaitingToQueued()

    /**
     * Swaps the engine requirement between NETWORK_UNMETERED and NETWORK as the Wi-Fi-only
     * setting changes.
     */
    suspend fun applyWifiOnly(wifiOnly: Boolean)

    /** Drops anything past its licence window and deletes the media (PRD §6.5). */
    suspend fun purgeExpired(nowMillis: Long): Int
}

/**
 * An episode to fetch at a chosen rung.
 *
 * [megabytes] is carried through from the sheet rather than recomputed, so the size the
 * viewer saw before starting is the size the Downloads list reports.
 */
data class DownloadRequest(
    val titleId: Int,
    val episode: Int,
    val rungId: String,
    val megabytes: Double,
    /**
     * Park it: the row shows as `waiting` and is withheld from the download engine until
     * the connection is unmetered (FR-303).
     */
    val queuedForWifi: Boolean,
    /**
     * The viewer chose "Use mobile data now" while on a metered connection. Only this
     * grants the engine permission to spend mobile data while Wi-Fi-only is on.
     */
    val allowMetered: Boolean = false,
)
