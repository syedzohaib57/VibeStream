package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.remote.jellyfin.JellyfinApi
import com.example.streamingappzb.data.remote.jellyfin.JfItemDto
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType

/**
 * One connected Jellyfin server: address, token, and how to speak to it.
 *
 * Normalised once at construction so every URL built from [baseUrl] is well-formed:
 * the viewer types `192.168.1.5:8096` or `https://media.example.com/`, and both become
 * a scheme-qualified address with no trailing slash.
 */
data class JellyfinConfig(
    val rawUrl: String,
    val token: String,
) {
    val baseUrl: String = normalise(rawUrl)

    /** The host alone, for attribution lines — the token must never appear in UI. */
    val displayHost: String = baseUrl.substringAfter("://").substringBefore('/')

    companion object {
        /**
         * Scheme defaulted to `http`, not `https`: the overwhelmingly common Jellyfin is
         * a box on the LAN with no certificate, and defaulting to https would make the
         * common case fail with a handshake error the connect dialog cannot explain.
         * Anyone running TLS types the scheme, which is preserved exactly.
         */
        fun normalise(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.contains("://")) trimmed else "http://$trimmed"
        }
    }
}

/**
 * Plays the real file from the viewer's own media server.
 *
 * This is the "fetch online" source: the library lives on a machine the viewer runs, and
 * every title it holds streams in full over HTTP from anywhere that machine is reachable.
 * Same seam as every other source, so the app needs no new screens to use it — the play
 * button simply starts resolving.
 *
 * ### Matching is by TMDB id first, and that is the point of choosing Jellyfin
 *
 * Jellyfin scrapes TMDB when it indexes a library, so its items carry the *same ids this
 * catalogue is keyed by*. `AnyProviderIdEquals` turns that into an exact join — no
 * filename parsing, no title fuzz. The free-text fallback exists only for items Jellyfin
 * failed to identify, and it still demands a year agreement before believing a name.
 *
 * ### The stream URL is static direct-play
 *
 * `/Videos/{id}/stream?static=true` serves the file bytes as they are on disk, which
 * ExoPlayer sniffs and plays — mp4 and mkv natively. Deliberately not the HLS transcode
 * endpoint: transcoding needs a session handshake, burns the server's CPU, and for a
 * personal server the file is almost always directly playable anyway.
 */
class JellyfinSourceRepository(
    private val api: JellyfinApi,
    /** Read per lookup: connecting in the library sheet takes effect immediately. */
    private val config: () -> JellyfinConfig?,
) : FreeSourceRepository {

    override suspend fun sourceFor(item: MediaItem): PlayableSource? =
        sourceFor(item, episode = null)

    override suspend fun sourceFor(item: MediaItem, episode: EpisodeRef?): PlayableSource? {
        val config = config() ?: return null
        return when (item.type) {
            MediaType.Movie -> movie(config, item)
            MediaType.Tv, MediaType.Anime -> episode(config, item, episode)
        }
    }

    // ----------------------------------------------------------------- movies

    private suspend fun movie(config: JellyfinConfig, item: MediaItem): PlayableSource? {
        val found = findItem(config, item, JellyfinApi.TYPE_MOVIE) ?: return null
        return source(
            config = config,
            itemId = found.id,
            label = item.title,
        )
    }

    // --------------------------------------------------------------- episodes

    /**
     * A series resolves through its episodes endpoint rather than a per-episode search:
     * Jellyfin numbers seasons and episodes exactly as TMDB does, so (season, episode)
     * picks the file deterministically.
     *
     * No [ref] means "start the show" — the first episode of the lowest real season.
     * Season 0 is Jellyfin's specials bucket, which is never the right starting point.
     */
    private suspend fun episode(
        config: JellyfinConfig,
        item: MediaItem,
        ref: EpisodeRef?,
    ): PlayableSource? {
        val series = findItem(config, item, JellyfinApi.TYPE_SERIES) ?: return null

        val episodes = runCatching {
            api.episodes(
                url = "${config.baseUrl}/Shows/${series.id}/Episodes",
                authHeader = authHeader(config.token),
                season = ref?.season,
            )
        }.getOrNull()?.items.orEmpty()

        val picked = if (ref != null) {
            episodes.firstOrNull {
                it.parentIndexNumber == ref.season && it.indexNumber == ref.episode
            } ?: return null
        } else {
            episodes
                .filter { (it.parentIndexNumber ?: 0) > 0 && it.indexNumber != null }
                .minWithOrNull(
                    compareBy({ it.parentIndexNumber ?: Int.MAX_VALUE }, { it.indexNumber ?: Int.MAX_VALUE }),
                ) ?: return null
        }

        val code = "S%02dE%02d".format(picked.parentIndexNumber ?: 0, picked.indexNumber ?: 0)
        return source(
            config = config,
            itemId = picked.id,
            label = "${item.title} · $code",
        )
    }

    // ----------------------------------------------------------------- lookup

    /** Provider-id join first; free-text search with a year check as the fallback. */
    private suspend fun findItem(
        config: JellyfinConfig,
        item: MediaItem,
        type: String,
    ): JfItemDto? {
        val auth = authHeader(config.token)
        val itemsUrl = "${config.baseUrl}/Items"

        // Anime ids are AniList's, not TMDB's — the AniList metadata plugin stores them
        // under its own provider key. Asking for Tmdb=<anilist id> would join two
        // unrelated numbering spaces, which is how someone else's film starts playing.
        val providerKey = if (item.type == MediaType.Anime) "AniList" else "Tmdb"

        val byId = runCatching {
            api.itemsByProviderId(
                url = itemsUrl,
                authHeader = auth,
                providerId = "$providerKey=${item.id}",
                includeTypes = type,
            )
        }.getOrNull()?.items.orEmpty().firstOrNull { it.id.isNotBlank() }
        if (byId != null) return byId

        val byName = runCatching {
            api.itemsByName(
                url = itemsUrl,
                authHeader = auth,
                searchTerm = item.title,
                includeTypes = type,
            )
        }.getOrNull()?.items.orEmpty()

        return byName.firstOrNull { candidate ->
            candidate.id.isNotBlank() &&
                titlesAgree(candidate.name, item.title) &&
                yearsAgree(candidate.productionYear, item.year, item.type)
        }
    }

    /**
     * The name fallback believes a title only when the year backs it up.
     *
     * A film's year must agree within one; a series is exempt for the same reason as in
     * [TitleMatch]: the catalogue's year is the first air date, which later seasons
     * legitimately contradict.
     */
    private fun yearsAgree(candidate: Int?, wanted: Int?, type: MediaType): Boolean {
        if (type.isSeries) return true
        if (candidate == null || wanted == null) return true
        return kotlin.math.abs(candidate - wanted) <= 1
    }

    private fun titlesAgree(candidate: String?, wanted: String): Boolean {
        val a = TitleMatch.normalise(candidate.orEmpty())
        val b = TitleMatch.normalise(wanted)
        return a.isNotEmpty() && a == b
    }

    // ----------------------------------------------------------------- stream

    private fun source(config: JellyfinConfig, itemId: String, label: String): PlayableSource =
        PlayableSource(
            // `static=true` is the direct-play contract; `api_key` because ExoPlayer's
            // requests carry no Authorization header.
            url = "${config.baseUrl}/Videos/$itemId/stream?static=true&api_key=${config.token}",
            type = StreamType.Progressive,
            label = label,
            attribution = "Your server · ${config.displayHost}",
            // Unknown container — mkv as often as mp4 — so let ExoPlayer sniff it.
            mimeType = null,
        )

    companion object {
        /**
         * Jellyfin's device identity header. Mandatory on authentication, accepted
         * everywhere else, and what names this app in the server's Devices dashboard.
         */
        fun authHeader(token: String?): String = buildString {
            append("MediaBrowser Client=\"VibeStream\", Device=\"Android\", ")
            append("DeviceId=\"vibestream-android\", Version=\"1.0\"")
            if (!token.isNullOrBlank()) append(", Token=\"$token\"")
        }
    }
}
