package com.example.streamingappzb.domain.model

/**
 * A TMDB genre, by id and name.
 *
 * Row endpoints return ids and nothing else, so the names have to come from somewhere.
 * They used to come from a hardcoded map; they now come from TMDB and are cached, with the
 * map as the cold-start fallback — see
 * [com.example.streamingappzb.data.remote.tmdb.TmdbGenres].
 */
data class MediaGenre(
    val id: Int,
    val name: String,
    /** Which catalogue the id belongs to. The two id spaces overlap. */
    val scope: GenreScope,
) {
    /** Stable across both scopes, for a Room primary key and for list diffing. */
    val key: String get() = "${scope.name}:$id"
}

enum class GenreScope {
    Movie,
    Tv,
    ;

    val mediaType: MediaType get() = if (this == Movie) MediaType.Movie else MediaType.Tv
}

/**
 * A studio or a broadcaster — TMDB models them as separate resources with the same shape,
 * so one type serves both and [isNetwork] says which endpoint it came from.
 */
data class Studio(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val originCountry: String?,
    val isNetwork: Boolean,
)

/** A film series, e.g. every Middle-earth film from any one of them. */
data class MovieCollection(
    val id: Int,
    val name: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val parts: List<MediaItem>,
) {
    /** A collection of one is the film you are already looking at, so it is not a row. */
    val isWorthShowing: Boolean get() = parts.size > 1
}

/**
 * A person and what they have been in.
 *
 * [asCast] and [asCrew] are kept apart rather than merged: an actor's list should lead with
 * what they acted in, and a director's with what they directed. [knownFor] picks which.
 */
data class PersonProfile(
    val id: Int,
    val name: String,
    val biography: String,
    val knownFor: String?,
    val profilePath: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val asCast: List<Credit>,
    val asCrew: List<Credit>,
) {
    /** The filmography to show first, newest at the top. */
    val primaryCredits: List<Credit>
        get() = if (knownFor.equals("Directing", ignoreCase = true) && asCrew.isNotEmpty()) {
            asCrew
        } else {
            asCast
        }

    val lifespan: String?
        get() = when {
            birthday == null -> null
            deathday == null -> birthday.take(4)
            else -> "${birthday.take(4)}–${deathday.take(4)}"
        }
}

/** One entry in a filmography: the title, plus what they did on it. */
data class Credit(
    val item: MediaItem,
    /** "Ellen Ripley", or "Director". Null when TMDB records neither. */
    val role: String?,
)

/**
 * A title's image set.
 *
 * [logoPath] is the point of this type. A title's own wordmark over a textless backdrop is
 * how a hero is meant to look and the API exposes it nowhere else — the detail endpoint's
 * `backdrop_path` always has the title burned in or not at all, with no way to tell which.
 */
data class TitleImages(
    val logoPath: String?,
    /** Textless, best-rated first — safe to draw a title over. */
    val backdrops: List<String>,
    val posters: List<String>,
) {
    val isEmpty: Boolean get() = logoPath == null && backdrops.isEmpty() && posters.isEmpty()

    companion object {
        val EMPTY = TitleImages(null, emptyList(), emptyList())
    }
}

/**
 * A discover query, as the browse screens express it.
 *
 * One type rather than a method per axis because TMDB's `/discover` takes all of these at
 * once, and a screen that filters by genre *and* sorts by rating is the same request.
 */
data class BrowseQuery(
    val type: MediaType,
    val genreId: Int? = null,
    val companyId: Int? = null,
    val networkId: Int? = null,
    val sort: BrowseSort = BrowseSort.Popular,
    val page: Int = 1,
) {
    /** Row/cache identity. Two queries differing only by page share it. */
    val key: String get() = "${type.name}:g$genreId:c$companyId:n$networkId:${sort.name}"
}

enum class BrowseSort {
    Popular,
    TopRated,
    Newest,
}
