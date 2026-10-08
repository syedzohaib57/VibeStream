package com.example.streamingappzb.data.remote.tmdb

import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.GenreScope

/**
 * TMDB's genre ids, as a cold-start fallback.
 *
 * The live list now comes from `genre/movie/list` and `genre/tv/list` and is cached in Room
 * by [com.example.streamingappzb.data.media.GenreRepositoryImpl]. This map is what answers
 * before that cache exists — the first frame after install, and any build with no network.
 *
 * It stays because the alternative is a blank subtitle under every poster on first run:
 * genre names are needed to *draw* a row, so they cannot wait on a round trip. The list has
 * been stable for years, so being a release or two behind costs nothing.
 */
object TmdbGenres {

    /** Film genre ids. */
    private val MOVIE: Map<Int, String> = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
    )

    /**
     * Television genre ids. A separate space from [MOVIE] — the shared ids mean the same
     * thing, but the television-only ones do not exist for film and vice versa.
     */
    private val TV: Map<Int, String> = mapOf(
        10759 to "Action & Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        10762 to "Kids",
        9648 to "Mystery",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics",
        37 to "Western",
    )

    const val ID_ANIMATION = 16
    const val ID_DOCUMENTARY = 99

    /** Either space, film first — callers at this level do not know the type. */
    fun name(id: Int): String? = MOVIE[id] ?: TV[id]

    /** At most [limit], because the metadata row is one line and must not wrap. */
    fun names(ids: List<Int>, limit: Int = 3): List<String> =
        ids.mapNotNull(::name).take(limit)

    /** The built-in list for one space, shaped like the live one. */
    fun fallback(scope: GenreScope): List<MediaGenre> = when (scope) {
        GenreScope.Movie -> MOVIE
        GenreScope.Tv -> TV
    }
        .map { (id, name) -> MediaGenre(id = id, name = name, scope = scope) }
        .sortedBy { it.name }
}
