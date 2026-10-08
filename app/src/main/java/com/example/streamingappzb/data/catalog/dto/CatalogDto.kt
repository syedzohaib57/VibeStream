package com.example.streamingappzb.data.catalog.dto

import com.squareup.moshi.JsonClass

/**
 * Wire shape of `assets/catalog.json`, which is deliberately the same shape a real
 * `GET /home` + `GET /titles/{id}` would return (PRD §7). Unknown keys are ignored, so
 * the `_comment` documentation blocks in the asset cost nothing.
 *
 * Every field has a default: a partial or older payload degrades rather than throwing.
 */
@JsonClass(generateAdapter = true)
data class CatalogDto(
    val version: Int = 1,
    val streams: Map<String, StreamDto> = emptyMap(),
    val ads: AdConfigDto = AdConfigDto(),
    val drmTestVector: DrmTestVectorDto? = null,
    val rungs: List<RungDto> = emptyList(),
    val heroByKind: Map<String, Int> = emptyMap(),
    val rows: List<RowDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val episodeNames: List<String> = emptyList(),
    val titles: List<TitleDto> = emptyList(),
    val continueWatching: List<ContinueDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class StreamDto(
    val url: String,
    val type: String = "hls",
    val hasSubtitles: Boolean = false,
    val drm: DrmDto? = null,
)

@JsonClass(generateAdapter = true)
data class DrmDto(
    val scheme: String = "widevine",
    val licenseUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The public Widevine test asset used to exercise the offline-licence path from debug
 * builds. No shipped title points at it, so a rotated token can never break the
 * catalogue.
 */
@JsonClass(generateAdapter = true)
data class DrmTestVectorDto(
    val name: String,
    val url: String,
    val type: String = "dash",
    val drm: DrmDto? = null,
)

@JsonClass(generateAdapter = true)
data class AdConfigDto(
    /** One pre-roll, at most 30 s (PRD §6.3). */
    val preRollSeconds: Int = 15,
    /** Skip unlocks at exactly this second of ad playback. */
    val skipAfterSeconds: Int = 5,
    val creatives: List<CreativeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreativeDto(
    val url: String,
    val type: String = "progressive",
)

@JsonClass(generateAdapter = true)
data class RungDto(
    val id: String,
    val subKey: String,
    val mbPerHour: Int,
    val maxHeight: Int,
)

@JsonClass(generateAdapter = true)
data class RowDto(
    val key: String,
    val title: String,
    val ids: List<Int> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GenreDto(
    val name: String,
    val c1: String,
    val motif: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TitleDto(
    val id: Int,
    val title: String,
    val kind: String,
    val year: Int,
    /** Episode count for a series. */
    val eps: Int? = null,
    /** Runtime in minutes for a Film. */
    val mins: Int? = null,
    val genre: String = "",
    val synopsis: String = "",
    val cast: List<String> = emptyList(),
    val c1: String = "#18181B",
    val c2: String = "#050505",
    val motif: Int = 0,
    /** Real key art. Absent for every sample title, so the drawn poster is the norm. */
    val artUrl: String? = null,
    val downloadable: Boolean = true,
    val fresh: Boolean = false,
    /** Key into [CatalogDto.streams]. */
    val stream: String = "",
    val adBreaks: List<Float> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ContinueDto(
    val titleId: Int,
    val ep: Int,
    val posSec: Int,
)
