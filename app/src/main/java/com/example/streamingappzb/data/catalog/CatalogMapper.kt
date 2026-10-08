package com.example.streamingappzb.data.catalog

import com.example.streamingappzb.data.catalog.dto.GenreDto
import com.example.streamingappzb.data.catalog.dto.RowDto
import com.example.streamingappzb.data.catalog.dto.StreamDto
import com.example.streamingappzb.data.catalog.dto.TitleDto
import com.example.streamingappzb.data.db.DownloadEntity
import com.example.streamingappzb.data.db.GenreEntity
import com.example.streamingappzb.data.db.HomeRowEntity
import com.example.streamingappzb.data.db.ProgressEntity
import com.example.streamingappzb.data.db.TitleEntity
import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Drm
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Stream
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Title

/**
 * DTO <-> entity <-> domain conversions.
 *
 * Deliberately free of `android.graphics.Color` so colour parsing is unit-testable on the
 * JVM, and tolerant of malformed input: a bad colour falls back to the card surface rather
 * than crashing Home.
 */
object CatalogMapper {

    private const val CAST_SEPARATOR = "\n"
    private const val LIST_SEPARATOR = ","
    private const val HEADER_SEPARATOR = "\n"
    private const val HEADER_KV = ": "

    private val FALLBACK_COLOR = 0xFF18181B.toInt()

    /** Accepts `#RRGGBB` and `#AARRGGBB`; anything else becomes the card surface. */
    fun parseColor(hex: String?): Int {
        val s = hex?.trim()?.removePrefix("#") ?: return FALLBACK_COLOR
        return runCatching {
            when (s.length) {
                6 -> (0xFF000000L or s.toLong(16)).toInt()
                8 -> s.toLong(16).toInt()
                else -> FALLBACK_COLOR
            }
        }.getOrDefault(FALLBACK_COLOR)
    }

    // ---------- Titles ----------

    fun toEntity(dto: TitleDto, streams: Map<String, StreamDto>, sortOrder: Int): TitleEntity {
        val stream = streams[dto.stream]
        return TitleEntity(
            id = dto.id,
            title = dto.title,
            kind = Kind.from(dto.kind).name,
            year = dto.year,
            episodeCount = dto.eps,
            runtimeMinutes = dto.mins,
            genre = dto.genre,
            synopsis = dto.synopsis,
            cast = dto.cast.joinToString(CAST_SEPARATOR),
            c1 = parseColor(dto.c1),
            c2 = parseColor(dto.c2),
            motif = dto.motif,
            artUrl = dto.artUrl,
            downloadable = dto.downloadable,
            fresh = dto.fresh,
            adBreaks = dto.adBreaks.joinToString(LIST_SEPARATOR),
            streamUrl = stream?.url.orEmpty(),
            streamType = StreamType.from(stream?.type).name,
            hasSubtitles = stream?.hasSubtitles ?: false,
            drmScheme = stream?.drm?.scheme,
            drmLicenseUrl = stream?.drm?.licenseUrl,
            drmHeaders = stream?.drm?.headers
                ?.takeIf { it.isNotEmpty() }
                ?.entries
                ?.joinToString(HEADER_SEPARATOR) { "${it.key}$HEADER_KV${it.value}" },
            sortOrder = sortOrder,
        )
    }

    fun toDomain(e: TitleEntity): Title = Title(
        id = e.id,
        title = e.title,
        kind = Kind.from(e.kind),
        year = e.year,
        episodeCount = e.episodeCount,
        runtimeMinutes = e.runtimeMinutes,
        genre = e.genre,
        synopsis = e.synopsis,
        cast = e.cast.split(CAST_SEPARATOR).filter(String::isNotBlank),
        c1 = e.c1,
        c2 = e.c2,
        motif = e.motif,
        artUrl = e.artUrl,
        downloadable = e.downloadable,
        fresh = e.fresh,
        adBreaks = e.adBreaks.split(LIST_SEPARATOR)
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it > 0f && it < 1f }
            .sorted(),
        stream = Stream(
            url = e.streamUrl,
            type = StreamType.valueOf(e.streamType),
            hasSubtitles = e.hasSubtitles,
            drm = if (e.drmScheme != null && e.drmLicenseUrl != null) {
                Drm(
                    scheme = e.drmScheme,
                    licenseUrl = e.drmLicenseUrl,
                    headers = e.drmHeaders
                        ?.split(HEADER_SEPARATOR)
                        ?.mapNotNull { line ->
                            val i = line.indexOf(HEADER_KV)
                            if (i <= 0) null else line.take(i) to line.substring(i + HEADER_KV.length)
                        }
                        ?.toMap()
                        .orEmpty(),
                )
            } else {
                null
            },
        ),
    )

    // ---------- Rows and genres ----------

    fun toEntity(dto: RowDto, sortOrder: Int) = HomeRowEntity(
        key = dto.key,
        title = dto.title,
        titleIds = dto.ids.joinToString(LIST_SEPARATOR),
        sortOrder = sortOrder,
    )

    fun rowTitleIds(e: HomeRowEntity): List<Int> =
        e.titleIds.split(LIST_SEPARATOR).mapNotNull { it.trim().toIntOrNull() }

    fun toEntity(dto: GenreDto, sortOrder: Int) = GenreEntity(
        name = dto.name,
        c1 = parseColor(dto.c1),
        motif = dto.motif,
        sortOrder = sortOrder,
    )

    fun toDomain(e: GenreEntity) = Genre(name = e.name, c1 = e.c1, motif = e.motif)

    // ---------- Progress ----------

    fun toDomain(e: ProgressEntity) = Progress(
        titleId = e.titleId,
        episode = e.episode,
        positionSeconds = e.positionSeconds,
        updatedAt = e.updatedAt,
    )

    fun toEntity(p: Progress) = ProgressEntity(
        titleId = p.titleId,
        episode = p.episode,
        positionSeconds = p.positionSeconds,
        updatedAt = p.updatedAt,
    )

    // ---------- Downloads ----------

    fun toDomain(e: DownloadEntity) = DownloadItem(
        titleId = e.titleId,
        episode = e.episode,
        state = runCatching { DlState.valueOf(e.state) }.getOrDefault(DlState.Queued),
        percent = e.percent,
        rungId = e.rungId,
        megabytes = e.megabytes,
        expiresAtMillis = e.expiresAtMillis,
        queuedForWifi = e.queuedForWifi,
    )

}
