package com.example.streamingappzb.domain.model

/**
 * A catalogue title (PRD §7).
 *
 * [c1]/[c2] and [motif] are the poster's own art: when [artUrl] is absent, PosterView
 * paints a gradient from c1 to c2 under one of four fixed light motifs. That fallback
 * has to look finished, not like an error (PRD §5) — every sample title ships without
 * key art, so it is the normal case, not the exception.
 *
 * [downloadable] is the rights record. A false value means the glyph shows a lock and a
 * download can never be enqueued (PRD §6.2).
 */
data class Title(
    val id: Int,
    val title: String,
    val kind: Kind,
    val year: Int,
    /** Episode count for a series, null for a Film. */
    val episodeCount: Int?,
    /** Runtime in minutes for a Film, null for a series. */
    val runtimeMinutes: Int?,
    /** The design's own string, e.g. "Romance · Family" — fuzzy search matches on it. */
    val genre: String,
    val synopsis: String,
    val cast: List<String>,
    val c1: Int,
    val c2: Int,
    val motif: Int,
    val artUrl: String?,
    val downloadable: Boolean,
    val fresh: Boolean,
    /** Mid-roll positions as fractions of runtime, authored per title (PRD §6.3). */
    val adBreaks: List<Float>,
    val stream: Stream,
) {
    val isFilm: Boolean get() = kind == Kind.Film

    /** Genres joined for display: the hero shows them with a bullet (PRD §6.1). */
    val genreList: List<String> get() = genre.split(" · ").map(String::trim).filter(String::isNotEmpty)

    /**
     * "New episodes" is cyan on the hero, and only ever appears on a fresh *series* —
     * a film cannot have new episodes (PRD §6.1, acceptance item 4).
     */
    val showsNewEpisodes: Boolean get() = fresh && kind != Kind.Film

    /** Total episodes, treating a Film as a single one (mirrors Catalog.jsx episodesOf). */
    val totalEpisodes: Int get() = if (isFilm) 1 else (episodeCount ?: 0)
}

/** Where the video comes from, and the DRM it needs (if any). */
data class Stream(
    val url: String,
    val type: StreamType,
    val hasSubtitles: Boolean = false,
    val drm: Drm? = null,
)

/**
 * Widevine configuration. Downloads acquire an *offline* licence, and its real remaining
 * duration becomes the expiry shown in the Downloads list (PRD §6.5).
 *
 * All shipped content is clear, so this is null in practice; the path exists and is
 * exercised by the Axinom public test vector from debug builds.
 */
data class Drm(
    val scheme: String,
    val licenseUrl: String,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        const val SCHEME_WIDEVINE = "widevine"
    }
}

/**
 * One episode. A Film is modelled as a single episode named after the title, exactly as
 * the design does, so the player and the progress store need no special case.
 */
data class Episode(
    val titleId: Int,
    val number: Int,
    val name: String,
    val durationSeconds: Int,
)

/** A title plus its episodes — what `GET /titles/{id}` returns. */
data class TitleDetail(
    val title: Title,
    val episodes: List<Episode>,
) {
    fun episode(number: Int): Episode? = episodes.firstOrNull { it.number == number }

    /** The successor used by Up next (FR-206); null on the last episode. */
    fun next(after: Int): Episode? = episode(after + 1)
}

/** A genre browse tile (PRD §6.4), drawn from its own colour and motif. */
data class Genre(
    val name: String,
    val c1: Int,
    val motif: Int,
)
