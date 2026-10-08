package com.example.streamingappzb.domain.model

import kotlin.math.ceil

/**
 * One downloaded (or downloading) episode.
 *
 * [megabytes] is the estimate the download sheet showed *before* the download started
 * (FR-302), computed by [megabytesFor], so what the Downloads list reports is the same
 * number the viewer agreed to.
 *
 * [expiresAtMillis] is the licence window: the real remaining duration of the Widevine
 * offline licence for DRM content, or a local 30-day window for clear content.
 */
data class DownloadItem(
    val titleId: Int,
    val episode: Int,
    val state: DlState,
    val percent: Int,
    val rungId: String,
    val megabytes: Double,
    val expiresAtMillis: Long?,
    /**
     * True when the viewer chose "Queue for Wi-Fi" on mobile data (FR-303). It is what
     * separates [DlState.Waiting] from [DlState.Queued]; the network monitor clears it
     * when the connection turns unmetered.
     */
    val queuedForWifi: Boolean = false,
) {
    val key: String get() = key(titleId, episode)

    fun expiresInDays(nowMillis: Long): Int? = expiresAtMillis?.let {
        ceil((it - nowMillis).toDouble() / DAY_MILLIS).toInt().coerceAtLeast(0)
    }

    fun isExpired(nowMillis: Long): Boolean = expiresAtMillis != null && expiresAtMillis <= nowMillis

    companion object {
        fun key(titleId: Int, episode: Int): String = "$titleId-$episode"

        /** Licence window for clear content, matching what the design mocks up. */
        const val DEFAULT_LICENCE_DAYS = 30

        /** Under this, the expiry reads in rose rather than textMid (PRD §6.5). */
        const val EXPIRY_WARNING_DAYS = 7

        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** A Downloads-list row: the item plus what it takes to draw it. */
data class DownloadRow(
    val item: DownloadItem,
    val title: Title,
    val episode: Episode,
)

/**
 * What the Downloads screen shows, already split into the PRD's two sections so the
 * fragment does no filtering of its own.
 */
data class DownloadsView(
    val inProgress: List<DownloadRow>,
    val ready: List<DownloadRow>,
    val wifiOnly: Boolean,
    val smartDownloads: Boolean,
    val network: NetworkState,
) {
    val isEmpty: Boolean get() = inProgress.isEmpty() && ready.isEmpty()
    val usedMegabytes: Double get() = ready.sumOf { it.item.megabytes }
}
