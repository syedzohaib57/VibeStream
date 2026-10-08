package com.example.streamingappzb.data.remote.anilist

import com.example.streamingappzb.domain.model.CastMember
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.NextAiring
import com.example.streamingappzb.domain.model.Trailer
import com.example.streamingappzb.domain.model.WatchOptions

object AniListMappers {

    fun toItem(dto: AniListMediaDto): MediaItem? {
        val title = dto.title?.display?.takeIf { it.isNotBlank() } ?: return null
        if (dto.id <= 0) return null
        return MediaItem(
            id = dto.id,
            type = MediaType.Anime,
            title = title,
            overview = stripHtml(dto.description),
            posterPath = dto.coverImage?.best,
            backdropPath = dto.bannerImage ?: dto.coverImage?.best,
            year = dto.seasonYear ?: dto.startDate?.year,
            // AniList scores out of 100; everything else in the app is out of 10.
            rating = dto.averageScore?.takeIf { it > 0 }?.let { it / 10.0 },
            genres = dto.genres.take(3),
            releaseDate = dto.startDate?.iso,
        )
    }

    fun toItems(dtos: List<AniListMediaDto>): List<MediaItem> =
        dtos.mapNotNull(::toItem).distinctBy { it.id }

    /**
     * @param watch provider data comes from TMDB, not AniList, so the caller supplies
     *   whatever it managed to resolve — usually by matching the title on TMDB.
     */
    fun toDetail(dto: AniListMediaDto, watch: WatchOptions): MediaDetail? {
        val item = toItem(dto) ?: return null
        val studio = dto.studios?.nodes?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        return MediaDetail(
            item = item,
            tagline = studio?.let { "Studio $it" },
            runtimeMinutes = dto.duration?.takeIf { it > 0 },
            seasonCount = null,
            episodeCount = dto.episodes?.takeIf { it > 0 },
            status = dto.status?.let(::prettyStatus),
            cast = dto.characters?.nodes.orEmpty().mapNotNull { node ->
                node.name?.full?.takeIf { it.isNotBlank() }?.let {
                    // id 0: these are AniList characters, not TMDB people, so there is no
                    // filmography to open — the UI treats 0 as "not tappable".
                    CastMember(id = 0, name = it, character = null, profilePath = node.image?.best)
                }
            },
            trailers = listOfNotNull(toTrailer(dto.trailer, item.title)),
            seasons = emptyList(),
            similar = emptyList(),
            watch = watch,
            nextAiring = dto.nextAiringEpisode
                ?.takeIf { it.episode > 0 && it.airingAt > 0 }
                ?.let { NextAiring(it.episode, it.airingAt) },
        )
    }

    /** AniList carries trailers for several sites; only YouTube has a player here. */
    private fun toTrailer(dto: AniListTrailerDto?, title: String): Trailer? {
        val id = dto?.id?.takeIf { it.isNotBlank() } ?: return null
        if (!dto.site.equals("youtube", ignoreCase = true)) return null
        return Trailer(key = id, name = "$title trailer", official = true, type = "Trailer")
    }

    private fun prettyStatus(raw: String): String = raw
        .split('_')
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }

    /**
     * AniList descriptions contain a little HTML even with `asHtml: false` — `<br>`,
     * `<i>`, and the occasional `<b>`. Rendering them raw shows literal tags to the
     * viewer, so they are stripped rather than passed through.
     */
    fun stripHtml(raw: String?): String {
        val text = raw ?: return ""
        return text
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
