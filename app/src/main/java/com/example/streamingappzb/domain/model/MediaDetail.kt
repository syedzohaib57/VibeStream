package com.example.streamingappzb.domain.model

/**
 * Everything the title screen needs, assembled from one detail call with its
 * `append_to_response` extras (or one AniList query for anime).
 */
data class MediaDetail(
    val item: MediaItem,
    val tagline: String?,
    /** Minutes. For a series this is the average episode runtime. */
    val runtimeMinutes: Int?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    /** "Returning Series", "Ended", "Released", "Post Production"… as the source words it. */
    val status: String?,
    val cast: List<CastMember>,
    val trailers: List<Trailer>,
    val seasons: List<SeasonSummary>,
    val similar: List<MediaItem>,
    /** Where it can be watched, in the viewer's region. Empty when nobody carries it. */
    val watch: WatchOptions,
    /**
     * TMDB's recommendations — built from what viewers of this title went on to watch, and
     * noticeably better than [similar], which is keyword overlap. Empty for obscure titles,
     * which is why both are carried; see [related].
     */
    val recommendations: List<MediaItem> = emptyList(),
    /** Directors and writers, which the cast row does not cover. */
    val crew: List<CrewMember> = emptyList(),
    /** The studios or broadcasters behind it, for the "more from" row. */
    val studios: List<Studio> = emptyList(),
    /** Set when the film is part of a series. Fetched separately — only the stub is inline. */
    val collectionId: Int? = null,
    val collectionName: String? = null,
    /** Alternative art, including the title's own wordmark. See [TitleImages]. */
    val images: TitleImages = TitleImages.EMPTY,
    /** IMDb id, where a provider hand-off needs more than a title string. */
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val homepage: String? = null,
    /** Anime only: when the next episode airs. */
    val nextAiring: NextAiring? = null,
    /** Non-null when this title can legally be played in-app (see [PlayableSource]). */
    val playable: PlayableSource? = null,
) {
    val type: MediaType get() = item.type

    /** The best trailer to offer: an official one if there is any, else the first. */
    val bestTrailer: Trailer? get() = trailers.firstOrNull { it.official } ?: trailers.firstOrNull()

    /**
     * What to put in the "More like this" row: recommendations when TMDB has them, else
     * `similar`. Never both — the two lists overlap heavily and a merged row reads as
     * padded.
     */
    val related: List<MediaItem>
        get() = recommendations.ifEmpty { similar }

    /** Directors first; a viewer scanning crew is almost always looking for one. */
    val directors: List<CrewMember> get() = crew.filter { it.isDirector }

    /** Shown only when the original differs — otherwise it is the same line twice. */
    val alternateTitle: String?
        get() = originalTitle?.takeIf { it.isNotBlank() && it != item.title }
}

data class CastMember(
    /** TMDB person id, for opening their filmography. 0 when TMDB omits it. */
    val id: Int,
    val name: String,
    val character: String?,
    val profilePath: String?,
)

data class CrewMember(
    val id: Int,
    val name: String,
    /** "Director", "Screenplay", "Writer"… as TMDB words it. */
    val job: String,
    val profilePath: String?,
) {
    val isDirector: Boolean get() = job.equals("Director", ignoreCase = true)
}

/**
 * A trailer or clip on YouTube.
 *
 * It is never fed to ExoPlayer: extracting YouTube's media URLs breaks their terms.
 * Playback goes through the official IFrame player or hands off to the YouTube app,
 * which is what [com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase]
 * returns.
 */
data class Trailer(
    val key: String,
    val name: String,
    val official: Boolean,
    val type: String,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$key"

    /** The embed the in-app player loads. `playsinline` keeps it in our own chrome. */
    val embedUrl: String
        get() = "https://www.youtube.com/embed/$key?playsinline=1&rel=0&modestbranding=1"

    val isTrailer: Boolean get() = type.equals("Trailer", ignoreCase = true)
}

data class SeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterPath: String?,
    val airDate: String?,
)

data class EpisodeSummary(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val stillPath: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
) {
    /** Episodes dated in the future are listed but not presented as watchable. */
    fun hasAired(todayIso: String): Boolean = airDate != null && airDate <= todayIso
}

/** AniList's airing schedule for a currently-running anime. */
data class NextAiring(
    val episode: Int,
    val airingAtEpochSeconds: Long,
)

/**
 * A source this app may play in full, as opposed to hand off to a provider.
 *
 * Three things reach this: the viewer's own files, indexed from folders they pointed the
 * app at; public-domain and openly-licensed film from the Internet Archive; and the
 * bundled sample streams. Licensed catalogue video is not one of them and does not come
 * from a free API — those titles route to their rightful provider through [WatchOptions].
 */
data class PlayableSource(
    val url: String,
    val type: StreamType,
    val label: String,
    /** e.g. "Public domain · Internet Archive". Shown next to the play button. */
    val attribution: String,
    /**
     * The container's real MIME type where it is known, so a Matroska or AVI file from the
     * viewer's own library is not announced to ExoPlayer as `video/mp4`.
     *
     * Null means *let ExoPlayer sniff it*, which is the right answer far more often than a
     * guess: [StreamType.Progressive] used to be hard-mapped to `video/mp4`, which selects
     * the MP4 extractor outright and fails on a perfectly playable `.mkv`.
     */
    val mimeType: String? = null,
)
