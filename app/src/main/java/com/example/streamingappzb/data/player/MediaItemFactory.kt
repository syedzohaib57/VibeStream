package com.example.streamingappzb.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import com.example.streamingappzb.domain.model.AdCreative
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Drm
import com.example.streamingappzb.domain.model.OfflinePlayback
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.Stream
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Title

/**
 * Builds Media3 [MediaItem]s. Shared by playback and downloading so a title is described
 * identically in both — including its DRM configuration, which is what lets a download's
 * offline licence be reused at playback time.
 */
@OptIn(UnstableApi::class)
object MediaItemFactory {

    /**
     * @param offlineLicenseKeySetId the key set stored when the episode was downloaded.
     *   Supplying it is what allows a DRM title to play with no network at all.
     */
    fun forContent(
        title: Title,
        episode: Int,
        offlineLicenseKeySetId: ByteArray? = null,
    ): MediaItem = MediaItem.Builder()
        .setUri(title.stream.url)
        // The media id is the download key, so the player, the download index and our
        // Room mirror all agree on what identifies an episode.
        .setMediaId(DownloadItem.key(title.id, episode))
        .setMimeType(mimeTypeOf(title.stream.type))
        .apply {
            title.stream.drm?.let { drm ->
                setDrmConfiguration(drmConfiguration(drm, offlineLicenseKeySetId))
            }
        }
        .build()

    /**
     * A downloaded episode, pinned to exactly the renditions on disk.
     *
     * Built from the stored download request rather than the title's master playlist: only
     * the fetched rendition is cached, so without these stream keys adaptive selection will
     * ask for a variant that is not there and playback fails the moment there is no
     * network — which is the only moment offline playback matters.
     */
    fun forDownload(offline: OfflinePlayback, title: Title, episode: Int): MediaItem =
        MediaItem.Builder()
            .setUri(offline.uri)
            .setMediaId(DownloadItem.key(title.id, episode))
            .setMimeType(offline.mimeType)
            .setCustomCacheKey(offline.customCacheKey)
            .setStreamKeys(
                offline.streamKeys.map { StreamKey(it.periodIndex, it.groupIndex, it.streamIndex) },
            )
            .apply {
                title.stream.drm?.let { drm ->
                    setDrmConfiguration(drmConfiguration(drm, offline.keySetId))
                }
            }
            .build()

    /** A pre-roll or mid-roll creative. Never DRM-protected. */
    fun forAd(creative: AdCreative, spotId: String): MediaItem = MediaItem.Builder()
        .setUri(creative.url)
        .setMediaId("ad:$spotId")
        .setMimeType(mimeTypeOf(creative.type))
        .build()

    /**
     * A source the free-source layer resolved: a file from the viewer's own library, or a
     * public-domain film served over plain HTTP.
     *
     * No DRM configuration: nothing in this category is encrypted, and attaching an empty
     * one would open a session that never gets a licence. The media id is derived from the
     * URL rather than a download key because there is no catalogue row to key against.
     *
     * The MIME type is only ever declared when it is actually known. For an adaptive
     * manifest it has to be, because the extension does not always say so; for a
     * progressive file it is better left off, and ExoPlayer sniffs the container. Naming
     * every progressive source `video/mp4` — which is what [mimeTypeOf] does — picked the
     * MP4 extractor for Matroska and AVI files from a local library and failed them at the
     * first read.
     */
    fun forPlayableSource(source: PlayableSource): MediaItem = MediaItem.Builder()
        .setUri(source.url)
        .setMediaId("source:${source.url}")
        .setMimeType(
            source.mimeType
                ?: mimeTypeOf(source.type).takeIf { source.type != StreamType.Progressive },
        )
        .build()

    fun forStream(stream: Stream, mediaId: String): MediaItem = MediaItem.Builder()
        .setUri(stream.url)
        .setMediaId(mediaId)
        .setMimeType(mimeTypeOf(stream.type))
        .apply { stream.drm?.let { setDrmConfiguration(drmConfiguration(it, null)) } }
        .build()

    private fun drmConfiguration(
        drm: Drm,
        offlineLicenseKeySetId: ByteArray?,
    ): MediaItem.DrmConfiguration = MediaItem.DrmConfiguration.Builder(
        if (drm.scheme.equals(Drm.SCHEME_WIDEVINE, ignoreCase = true)) {
            C.WIDEVINE_UUID
        } else {
            C.CLEARKEY_UUID
        },
    )
        .setLicenseUri(drm.licenseUrl)
        .setLicenseRequestHeaders(drm.headers)
        // Required for offline licences: each download needs its own session rather than
        // sharing one across the playback.
        .setMultiSession(true)
        .setKeySetId(offlineLicenseKeySetId)
        .build()

    private fun mimeTypeOf(type: StreamType): String = when (type) {
        StreamType.Hls -> MimeTypes.APPLICATION_M3U8
        StreamType.Dash -> MimeTypes.APPLICATION_MPD
        StreamType.Progressive -> MimeTypes.VIDEO_MP4
    }
}
