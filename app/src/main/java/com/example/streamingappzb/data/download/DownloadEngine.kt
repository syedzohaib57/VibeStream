package com.example.streamingappzb.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors

/**
 * The Media3 download stack, built once for the process.
 *
 * Decisions that come straight from the PRD:
 *
 * - **One active download at a time, the rest queue** (§6.5).
 * - **`NETWORK_UNMETERED` when Wi-Fi-only is on, otherwise `NETWORK`** (§6.5). The
 *   requirement is swapped live by [setWifiOnly], which is how a queued item starts the
 *   moment the connection turns unmetered.
 * - Media lands in app-private storage, so no storage permission is ever needed and the
 *   files are removed with the app.
 */
@OptIn(UnstableApi::class)
class DownloadEngine(context: Context, okHttp: OkHttpClient) {

    private val databaseProvider = StandaloneDatabaseProvider(context)

    /**
     * No eviction: a download the viewer asked for must not disappear because the cache
     * filled up. Expiry is a licence decision, handled by LicenceExpiryWorker.
     */
    val cache: SimpleCache = SimpleCache(
        File(context.filesDir, DOWNLOAD_DIR),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    val httpDataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttp)

    /**
     * Reads from the cache and never writes to it, so streaming can serve a downloaded
     * episode with no network while a partial stream never masquerades as a completed
     * download.
     *
     * HTTP only — see [playbackDataSourceFactory], which is what playback actually uses.
     */
    private val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Playback factory, scheme-aware.
     *
     * [DefaultDataSource] dispatches on the URI scheme: `content://` goes to
     * ContentDataSource, `file://` to FileDataSource, and everything remote falls through
     * to the cache-backed HTTP factory above. Without this wrapper an OkHttpDataSource
     * received the `content://` URI from the personal library and threw on it, because
     * OkHttp can only open http and https — a local file was unplayable for the structural
     * reason that the player had no way to open anything that was not a URL.
     */
    val playbackDataSourceFactory: DataSource.Factory =
        DefaultDataSource.Factory(context, cacheDataSourceFactory)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        httpDataSourceFactory,
        Executors.newFixedThreadPool(DOWNLOAD_THREADS),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
    }

    /** Swaps the engine requirement as the Wi-Fi-only setting changes. */
    fun setWifiOnly(wifiOnly: Boolean) {
        downloadManager.requirements = if (wifiOnly) {
            Requirements(Requirements.NETWORK_UNMETERED)
        } else {
            Requirements(Requirements.NETWORK)
        }
    }

    companion object {
        /** PRD §6.5: only one active download at a time; the rest queue. */
        const val MAX_PARALLEL_DOWNLOADS = 1

        private const val DOWNLOAD_DIR = "downloads"

        /**
         * More than [MAX_PARALLEL_DOWNLOADS] because each download also runs its own
         * progress and index work.
         */
        private const val DOWNLOAD_THREADS = 3
    }
}
