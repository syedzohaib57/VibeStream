package com.example.streamingappzb.data.remote.tmdb

/**
 * TMDB image URLs.
 *
 * Sizes are requested to match where the image is drawn rather than always taking the
 * largest: a poster row on a phone needs w342, and pulling w780 for it costs the viewer
 * several megabytes of mobile data per screen for no visible difference. That is the same
 * data-cost discipline the rest of the app is built around.
 *
 * Paths that already look absolute are returned untouched, so AniList's own CDN URLs pass
 * straight through.
 */
object TmdbImages {

    const val BASE = "https://image.tmdb.org/t/p/"

    /** Poster in a horizontal row or a grid cell. */
    fun poster(path: String?): String? = url(path, "w342")

    /** Poster on the title screen, where it is the largest thing on screen. */
    fun posterLarge(path: String?): String? = url(path, "w500")

    /** Full-bleed hero and title-screen backdrop. */
    fun backdrop(path: String?): String? = url(path, "w780")

    /** Cast thumbnail. */
    fun profile(path: String?): String? = url(path, "w185")

    /** Provider logo — small, square, always over a light chip. */
    fun logo(path: String?): String? = url(path, "w92")

    /** Episode still. */
    fun still(path: String?): String? = url(path, "w300")

    private fun url(path: String?, size: String): String? {
        val clean = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return BASE + size + if (clean.startsWith("/")) clean else "/$clean"
    }
}
