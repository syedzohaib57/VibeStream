package com.example.streamingappzb.domain.model

/**
 * Exactly what a completed download holds, so playback can be restricted to it.
 *
 * Playing a downloaded episode from the title's master playlist is not enough: only the
 * rendition that was actually fetched is in the cache, and adaptive selection will happily
 * ask for a variant that is not — which fails the moment there is no network, i.e. exactly
 * when offline playback matters.
 *
 * [streamKeys] are the renditions the download covers. Passing them to the player pins
 * selection to what is on disk.
 */
data class OfflinePlayback(
    val uri: String,
    val mimeType: String?,
    val streamKeys: List<StreamKeyRef>,
    /** Widevine offline licence, so DRM content plays with no licence round trip. */
    val keySetId: ByteArray?,
    val customCacheKey: String?,
) {
    // ByteArray needs structural equality for this to behave as a value type.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OfflinePlayback) return false
        return uri == other.uri &&
            mimeType == other.mimeType &&
            streamKeys == other.streamKeys &&
            keySetId.contentEquals(other.keySetId) &&
            customCacheKey == other.customCacheKey
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + streamKeys.hashCode()
        result = 31 * result + (keySetId?.contentHashCode() ?: 0)
        result = 31 * result + (customCacheKey?.hashCode() ?: 0)
        return result
    }
}

/** One rendition inside an adaptive manifest. */
data class StreamKeyRef(
    val periodIndex: Int,
    val groupIndex: Int,
    val streamIndex: Int,
)
