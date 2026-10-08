package com.example.streamingappzb.domain.model

/**
 * What kind of thing a [MediaItem] is.
 *
 * Anime is deliberately its own type rather than a genre. It comes from a different
 * source (AniList) with data TMDB has no field for — airing schedules, seasons by
 * broadcast year, studio — and users look for it as its own category.
 */
enum class MediaType {
    Movie,
    Tv,
    Anime,
    ;

    val isSeries: Boolean get() = this != Movie

    companion object {
        /** TMDB's own discriminator, as returned by `/search/multi`. */
        fun fromTmdb(raw: String?): MediaType? = when (raw) {
            "movie" -> Movie
            "tv" -> Tv
            else -> null
        }
    }
}

/**
 * One title as it appears in a row, a grid or a search result.
 *
 * Identity is ([id], [type]): TMDB numbers movies and series in separate spaces, so
 * 1399 is both a film and *Game of Thrones*. Anime ids come from AniList and are kept
 * apart by the type for the same reason.
 */
data class MediaItem(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    /** TMDB path or an absolute AniList URL — resolve through `ImageUrl`. */
    val posterPath: String?,
    val backdropPath: String?,
    /** Release or first-air year; null when the date is unknown or unset. */
    val year: Int?,
    /** 0..10 as the source reports it; null when nothing has voted yet. */
    val rating: Double?,
    val genres: List<String> = emptyList(),
    /** ISO-8601 `yyyy-MM-dd`. Kept as text: it is only ever shown or compared. */
    val releaseDate: String? = null,
) {
    val key: String get() = key(id, type)

    /** Not yet released — drives the Upcoming row and the "Coming" badge. */
    fun isUpcoming(todayIso: String): Boolean {
        val date = releaseDate ?: return false
        return date > todayIso
    }

    val ratingOutOfTen: String? get() = rating?.takeIf { it > 0 }?.let { "%.1f".format(it) }

    companion object {
        fun key(id: Int, type: MediaType): String = "${type.name}:$id"
    }
}

/** A row on the discover screen: a heading and the titles under it. */
data class MediaRow(
    val key: String,
    val title: String,
    val items: List<MediaItem>,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * The `top10` row draws each poster over its rank numeral.
     *
     * Rank is the row's existing order, so nothing has to be scored here: the row is fed
     * by TMDB's trending/day endpoint, which already returns titles most-popular first.
     */
    val isRankedRow: Boolean get() = key == KEY_TOP10

    /** A ranked row stops at ten; the numeral stops reading as a rank past that. */
    val rankedItems: List<MediaItem> get() = if (isRankedRow) items.take(MAX_RANK) else items

    companion object {
        const val KEY_TRENDING = "trending"
        const val KEY_TRENDING_TODAY = "trending_today"
        const val KEY_TOP10 = "top10"

        /** Ten is the whole premise of the row. */
        const val MAX_RANK = 10
        const val KEY_POPULAR_MOVIES = "popular_movies"
        const val KEY_POPULAR_TV = "popular_tv"
        const val KEY_UPCOMING = "upcoming"
        const val KEY_TOP_RATED = "top_rated"
        const val KEY_NOW_PLAYING = "now_playing"
        const val KEY_ON_THE_AIR = "on_the_air"
        const val KEY_ANIMATION = "animation"
        const val KEY_DOCUMENTARIES = "documentaries"
        const val KEY_ANIME_SEASON = "anime_season"
        const val KEY_ANIME_TOP = "anime_top"

        /**
         * Films old enough to be out of copyright — the only row whose titles this app can
         * play in full rather than hand off. See `ArchiveFreeSourceRepository`.
         */
        const val KEY_PUBLIC_DOMAIN = "public_domain"
    }
}

/**
 * The discover feed for one tab.
 *
 * [hero] is simply the strongest item in the first non-empty row rather than an editorial
 * pick — there is no editor here, and picking the top of what is actually trending is
 * both honest and always populated.
 */
data class MediaFeed(
    val hero: MediaItem?,
    val rows: List<MediaRow>,
) {
    val isEmpty: Boolean get() = hero == null && rows.all { it.isEmpty }

    companion object {
        val EMPTY = MediaFeed(hero = null, rows = emptyList())

        /** Home shows at most this many rows, carried over from the original brief. */
        const val MAX_ROWS = 8
    }
}
