package com.example.streamingappzb.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSession
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.OfflineLicenseHelper
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.catalog.CatalogMapper
import com.example.streamingappzb.data.db.DownloadDao
import com.example.streamingappzb.data.db.DownloadEntity
import com.example.streamingappzb.data.player.MediaItemFactory
import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Drm
import com.example.streamingappzb.domain.model.OfflinePlayback
import com.example.streamingappzb.domain.model.StreamKeyRef
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.DownloadRepository
import com.example.streamingappzb.domain.repository.DownloadRequest
import com.example.streamingappzb.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Bridges Media3's [DownloadManager] to our Room mirror.
 *
 * Why a mirror at all: Media3 tracks bytes and its own state machine, but it has no idea
 * which rung the viewer picked, what MB figure they were shown before starting
 * (FR-302), or that they chose to defer to Wi-Fi (FR-303). Those live here.
 *
 * The key mechanic for FR-303 is that **a `waiting` item is never handed to the engine**.
 * Media3's requirements are global, so if a parked item were in its queue, relaxing the
 * requirement for one "Use mobile data now" download would start every parked one too.
 * Withholding them makes the two cases independent.
 */
@OptIn(UnstableApi::class)
class DownloadRepositoryImpl(
    private val context: Context,
    private val engine: DownloadEngine,
    private val dao: DownloadDao,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : DownloadRepository {

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            scope.launch { syncFromEngine(download) }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            scope.launch(io) { dao.delete(download.request.id) }
        }
    }

    init {
        engine.downloadManager.addListener(listener)
        // Media3's index is the source of truth for bytes and state; reconcile on start so
        // progress made while the process was dead shows up.
        scope.launch { reconcile() }
    }

    /**
     * Resumes anything left pending by a previous process, and repairs drift between our
     * Room mirror and Media3's own index.
     *
     * Two distinct failures are handled:
     *
     * 1. **Media3 has the download, but nothing is running it.** Its DownloadManager owns
     *    the queue but does not progress without its service, so after a crash or a
     *    swipe-away a queued item sits idle until [MhDownloadService] is started again.
     *
     * 2. **Room has the row but Media3 never got the request.** Enqueueing writes the Room
     *    row first (so the UI reflects the ask immediately) and only then fetches the
     *    manifest to pick tracks — if the process dies in between, the row is stranded in
     *    `queued` forever. Those are re-submitted here.
     *
     * `start` rather than `startForeground`, because this runs during Application.onCreate,
     * which may itself be a background launch (a worker, a boot) where starting a
     * foreground service is not permitted. If it is refused, the WorkManagerScheduler picks
     * the queue up when the requirements are next met.
     */
    fun resumePendingDownloads() {
        scope.launch {
            val pending = withContext(io) { dao.pending(DlState.Done.name) }
                // A parked item is deliberately withheld from the engine (FR-303).
                .filter { it.state != DlState.Waiting.name }
            if (pending.isEmpty()) return@launch

            val known = knownDownloadIds()
            for (entity in pending) {
                if (entity.key !in known) {
                    submit(entity.titleId, entity.episode, entity.rungId)
                }
            }
            runCatching { DownloadService.start(context, MhDownloadService::class.java) }
        }
    }

    /** The ids Media3's own download index holds. */
    private fun knownDownloadIds(): Set<String> = runCatching {
        engine.downloadManager.downloadIndex.getDownloads().use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.download.request.id)
            }
        }
    }.getOrDefault(emptySet())

    override fun observeAll(): Flow<List<DownloadItem>> =
        dao.observeAll().map { list -> list.map(CatalogMapper::toDomain) }

    override suspend fun all(): List<DownloadItem> = withContext(io) {
        dao.all().map(CatalogMapper::toDomain)
    }

    override suspend fun get(titleId: Int, episode: Int): DownloadItem? = withContext(io) {
        dao.byEpisode(titleId, episode)?.let(CatalogMapper::toDomain)
    }

    override suspend fun isDownloaded(titleId: Int, episode: Int): Boolean = withContext(io) {
        dao.isDone(titleId, episode, DlState.Done.name)
    }

    /**
     * Reads the request Media3 stored for this download, which carries the stream keys of
     * the rendition that was actually fetched. Pinning playback to those is what makes an
     * offline episode play with no network — without them, adaptive selection asks for a
     * variant that was never cached.
     */
    override suspend fun offlinePlayback(titleId: Int, episode: Int): OfflinePlayback? =
        withContext(io) {
            val key = DownloadItem.key(titleId, episode)
            if (!dao.isDone(titleId, episode, DlState.Done.name)) return@withContext null
            val download = runCatching { engine.downloadManager.downloadIndex.getDownload(key) }
                .getOrNull() ?: return@withContext null
            val request = download.request
            OfflinePlayback(
                uri = request.uri.toString(),
                mimeType = request.mimeType,
                streamKeys = request.streamKeys.map {
                    StreamKeyRef(it.periodIndex, it.groupIndex, it.streamIndex)
                },
                keySetId = request.keySetId,
                customCacheKey = request.customCacheKey,
            )
        }

    override suspend fun enqueue(requests: List<DownloadRequest>) {
        if (requests.isEmpty()) return
        val now = System.currentTimeMillis()

        // Room first: the Downloads screen has to reflect the ask immediately, even before
        // the manifest has been fetched to pick tracks.
        withContext(io) {
            dao.putAll(
                requests.map { r ->
                    DownloadEntity(
                        key = DownloadItem.key(r.titleId, r.episode),
                        titleId = r.titleId,
                        episode = r.episode,
                        state = if (r.queuedForWifi) DlState.Waiting.name else DlState.Queued.name,
                        percent = 0,
                        rungId = r.rungId,
                        megabytes = r.megabytes,
                        expiresAtMillis = null,
                        queuedForWifi = r.queuedForWifi,
                        allowMetered = r.allowMetered,
                        offlineLicenseKeySetId = null,
                        createdAt = now,
                    )
                },
            )
        }

        requests.forEach { r ->
            analytics.downloadRequest(
                titleId = r.titleId,
                episode = r.episode,
                rung = r.rungId,
                megabytes = r.megabytes.roundToInt(),
                queuedForWifi = r.queuedForWifi,
            )
        }

        refreshRequirements()
        requests.filterNot { it.queuedForWifi }.forEach { submit(it.titleId, it.episode, it.rungId) }
    }

    override suspend fun togglePause(key: String) {
        val entity = withContext(io) { dao.byKey(key) } ?: return
        when (DlState.valueOf(entity.state)) {
            DlState.Paused -> {
                DownloadService.sendSetStopReason(
                    context, MhDownloadService::class.java, key, Download.STOP_REASON_NONE, false,
                )
                withContext(io) { dao.updateState(key, DlState.Queued.name, entity.percent) }
            }

            // A parked item has nothing running to pause; resuming it means giving up on
            // waiting for Wi-Fi, so it goes straight into the engine.
            DlState.Waiting -> {
                withContext(io) {
                    dao.put(entity.copy(state = DlState.Queued.name, queuedForWifi = false, allowMetered = true))
                }
                refreshRequirements()
                submit(entity.titleId, entity.episode, entity.rungId)
            }

            DlState.Done -> Unit

            else -> {
                DownloadService.sendSetStopReason(
                    context, MhDownloadService::class.java, key, STOP_REASON_PAUSED, false,
                )
                withContext(io) { dao.updateState(key, DlState.Paused.name, entity.percent) }
            }
        }
    }

    override suspend fun remove(key: String) {
        val entity = withContext(io) { dao.byKey(key) }
        DownloadService.sendRemoveDownload(context, MhDownloadService::class.java, key, false)
        withContext(io) { dao.delete(key) }
        // Hand the offline licence back so the DRM key store does not leak entries.
        entity?.offlineLicenseKeySetId?.let { keySetId ->
            val title = catalog.title(entity.titleId)
            title?.stream?.drm?.let { releaseOfflineLicense(it, keySetId) }
        }
        refreshRequirements()
    }

    override suspend fun promoteWaitingToQueued() {
        val waiting = withContext(io) { dao.byState(DlState.Waiting.name) }
        if (waiting.isEmpty()) return
        withContext(io) { dao.promoteWaiting(DlState.Waiting.name, DlState.Queued.name) }
        refreshRequirements()
        waiting.forEach { submit(it.titleId, it.episode, it.rungId) }
    }

    override suspend fun applyWifiOnly(wifiOnly: Boolean) {
        refreshRequirements(wifiOnly)
    }

    override suspend fun purgeExpired(nowMillis: Long): Int {
        val expired = withContext(io) { dao.expired(nowMillis) }
        expired.forEach { remove(it.key) }
        return expired.size
    }

    // ---------- Engine plumbing ----------

    /**
     * NETWORK_UNMETERED while Wi-Fi-only is on, unless some unfinished item carries
     * explicit mobile-data consent (PRD §6.5 / FR-303).
     */
    private suspend fun refreshRequirements(wifiOnly: Boolean = settings.wifiOnly.value) {
        val consented = withContext(io) { dao.pendingMeteredConsent(DlState.Done.name) }
        engine.setWifiOnly(wifiOnly && consented == 0)
    }

    /**
     * Fetches the manifest, selects only the tracks the chosen rung allows, acquires an
     * offline licence if the title is DRM-protected, and hands the request to the service.
     *
     * Selecting tracks is what makes the FR-302 estimate honest: a "240p, 155 MB"
     * download really fetches the 240p rendition rather than whatever ABR would pick.
     */
    private suspend fun submit(titleId: Int, episode: Int, rungId: String) {
        val key = DownloadItem.key(titleId, episode)
        val detail = catalog.titleDetail(titleId) ?: return
        val title = detail.title
        if (title.stream.url.isBlank()) {
            withContext(io) { dao.updateState(key, DlState.Failed.name, 0) }
            return
        }
        val rung = catalog.rung(rungId)

        // The rung as track-selection parameters. Passed to the factory so preparation
        // already knows the ceiling, and re-applied per period below because
        // DownloadHelper starts with *no* selections — without this the request would
        // carry no stream keys and fetch every rendition, blowing past the MB estimate.
        val rungParameters = TrackSelectionParameters.Builder()
            .setMaxVideoSize(rung.maxWidth, rung.maxHeight)
            .setMaxVideoBitrate(rung.maxBitrateBps)
            .build()

        try {
            val mediaItem = MediaItemFactory.forContent(title, episode)
            val helper = DownloadHelper.Factory()
                .setDataSourceFactory(engine.httpDataSourceFactory)
                .setRenderersFactory(DefaultRenderersFactory(context))
                .setTrackSelectionParameters(rungParameters)
                .create(mediaItem)

            // False for a progressive stream, which has no tracks to choose between —
            // clearTrackSelections and addTrackSelection both throw in that mode.
            val tracksAvailable = helper.prepareSuspending()

            val request = try {
                if (tracksAvailable) {
                    for (periodIndex in 0 until helper.periodCount) {
                        helper.clearTrackSelections(periodIndex)
                        helper.addTrackSelection(periodIndex, rungParameters)
                    }
                }

                val base = helper.getDownloadRequest(key, null)
                title.stream.drm?.let { drm ->
                    val format = if (tracksAvailable) helper.drmInitFormat() else null
                    val keySetId = format?.let { acquireOfflineLicense(drm, it) }
                    if (keySetId != null) {
                        withContext(io) { dao.setKeySetId(key, keySetId) }
                        base.copyWithKeySetId(keySetId)
                    } else {
                        base
                    }
                } ?: base
            } finally {
                helper.release()
            }

            // foreground = true: enqueueing is always a user action from a visible screen,
            // and starting the service in the background instead would fall foul of
            // Android 12+ foreground-service-start restrictions.
            DownloadService.sendAddDownload(
                context, MhDownloadService::class.java, request, true,
            )
        } catch (e: IOException) {
            // A manifest that will not load is a failure the Downloads list must show,
            // not a silent no-op.
            withContext(io) { dao.updateState(key, DlState.Failed.name, 0) }
        } catch (e: InterruptedException) {
            withContext(io) { dao.updateState(key, DlState.Queued.name, 0) }
        }
    }

    /** Mirrors one Media3 download into Room. */
    private suspend fun syncFromEngine(download: Download) {
        val key = download.request.id
        val existing = withContext(io) { dao.byKey(key) } ?: return
        val percent = download.percentDownloaded
            .takeIf { it >= 0f && !it.isNaN() }
            ?.roundToInt()
            ?.coerceIn(0, 100)
            ?: existing.percent

        when (download.state) {
            Download.STATE_COMPLETED -> {
                val expiresAt = licenceExpiryFor(existing)
                withContext(io) { dao.markDone(key, DlState.Done.name, expiresAt) }
                analytics.downloadComplete(existing.titleId, existing.episode)
            }

            Download.STATE_DOWNLOADING ->
                withContext(io) { dao.updateState(key, DlState.Downloading.name, percent) }

            Download.STATE_QUEUED ->
                // Media3 reports QUEUED both for "next up" and "blocked by requirements".
                // Keep our own label when the viewer parked it.
                withContext(io) {
                    val state = if (existing.queuedForWifi) DlState.Waiting else DlState.Queued
                    dao.updateState(key, state.name, percent)
                }

            Download.STATE_STOPPED ->
                withContext(io) { dao.updateState(key, DlState.Paused.name, percent) }

            Download.STATE_FAILED ->
                withContext(io) { dao.updateState(key, DlState.Failed.name, percent) }

            Download.STATE_REMOVING, Download.STATE_RESTARTING -> Unit
        }
        refreshRequirements()
    }

    private suspend fun reconcile() {
        val index = runCatching {
            engine.downloadManager.downloadIndex.getDownloads().use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.download)
                }
            }
        }.getOrNull() ?: return
        index.forEach { syncFromEngine(it) }
        refreshRequirements()
    }

    /**
     * Licence window. Real remaining seconds for DRM content, otherwise the local 30-day
     * window the design mocks up (PRD §6.5).
     */
    private suspend fun licenceExpiryFor(entity: DownloadEntity): Long {
        val now = System.currentTimeMillis()
        val keySetId = entity.offlineLicenseKeySetId
        val drm = catalog.title(entity.titleId)?.stream?.drm
        if (keySetId != null && drm != null) {
            val remaining = remainingLicenceSeconds(drm, keySetId)
            if (remaining != null && remaining > 0) return now + remaining * 1_000L
        }
        return now + DownloadItem.DEFAULT_LICENCE_DAYS * DownloadItem.DAY_MILLIS
    }

    private suspend fun acquireOfflineLicense(drm: Drm, format: Format): ByteArray? =
        withContext(io) { withLicenseHelper(drm) { it.downloadLicense(format) } }

    private suspend fun remainingLicenceSeconds(drm: Drm, keySetId: ByteArray): Long? =
        withContext(io) {
            withLicenseHelper(drm) { helper ->
                // (licence duration, playback duration) — the smaller one governs.
                val remaining = helper.getLicenseDurationRemainingSec(keySetId)
                minOf(remaining.first, remaining.second)
            }
        }

    private suspend fun releaseOfflineLicense(drm: Drm, keySetId: ByteArray) {
        withContext(io) { withLicenseHelper(drm) { it.releaseLicense(keySetId) } }
    }

    /**
     * Builds an [OfflineLicenseHelper] that sends [Drm.headers] as **HTTP headers** on the
     * licence request, which is what token-gated licence servers require.
     * `newWidevineInstance`'s map parameter is key-request *parameters*, not headers, so
     * the callback is constructed directly.
     *
     * It is not AutoCloseable, hence the explicit release.
     */
    private fun <T> withLicenseHelper(drm: Drm, block: (OfflineLicenseHelper) -> T): T? {
        var helper: OfflineLicenseHelper? = null
        return try {
            val callback = HttpMediaDrmCallback(
                drm.licenseUrl,
                /* forceDefaultLicenseUrl = */ false,
                engine.httpDataSourceFactory,
            ).apply {
                drm.headers.forEach { (name, value) -> setKeyRequestProperty(name, value) }
            }
            val sessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                // Offline licences are acquired one at a time here.
                .setMultiSession(false)
                .build(callback)
            helper = OfflineLicenseHelper(
                sessionManager,
                DrmSessionEventListener.EventDispatcher(),
            )
            block(helper)
        } catch (e: DrmSession.DrmSessionException) {
            // A rotated or rejected token must not take a download down with it: the item
            // falls back to the local licence window.
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: IOException) {
            null
        } finally {
            runCatching { helper?.release() }
        }
    }

    private companion object {
        /** Any non-zero stop reason pauses a download; this one is ours. */
        const val STOP_REASON_PAUSED = 1
    }
}

/**
 * [DownloadHelper.prepare] is callback-based; this makes it awaitable.
 *
 * @return `tracksInfoAvailable` — true for an adaptive source whose tracks can be
 *   selected, false for a progressive one.
 */
@OptIn(UnstableApi::class)
private suspend fun DownloadHelper.prepareSuspending(): Boolean =
    suspendCancellableCoroutine { continuation ->
        prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                if (continuation.isActive) continuation.resume(tracksInfoAvailable)
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { runCatching { release() } }
    }

/**
 * The first DRM-initialised video format in the manifest, which is what
 * [OfflineLicenseHelper.downloadLicense] needs to build its key request.
 */
@OptIn(UnstableApi::class)
private fun DownloadHelper.drmInitFormat(): Format? {
    for (periodIndex in 0 until periodCount) {
        val mappedTrackInfo = getMappedTrackInfo(periodIndex)
        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
            val groups = mappedTrackInfo.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until groups.length) {
                val group = groups[groupIndex]
                for (formatIndex in 0 until group.length) {
                    val format = group.getFormat(formatIndex)
                    if (format.drmInitData != null) return format
                }
            }
        }
    }
    return null
}
