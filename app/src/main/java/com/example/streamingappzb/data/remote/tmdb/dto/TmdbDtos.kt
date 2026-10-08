package com.example.streamingappzb.data.remote.tmdb.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TMDB responses.
 *
 * Every field is nullable or defaulted. TMDB omits keys rather than sending nulls, and
 * omits different ones per endpoint and per locale — a non-null field here would mean the
 * whole screen fails on one missing poster.
 */

@JsonClass(generateAdapter = true)
data class TmdbPageDto<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 1,
    @Json(name = "total_results") val totalResults: Int = 0,
)

/** A row item. `/search/multi` also sets [mediaType]; the typed endpoints do not. */
@JsonClass(generateAdapter = true)
data class TmdbSummaryDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "genre_ids") val genreIds: List<Int> = emptyList(),
    @Json(name = "media_type") val mediaType: String? = null,
    val popularity: Double? = null,
) {
    /** Movies carry `title`, series carry `name`. */
    val displayTitle: String? get() = title ?: name

    val displayDate: String? get() = releaseDate ?: firstAirDate
}

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(val id: Int = 0, val name: String = "")

/** `genre/movie/list` and `genre/tv/list`. */
@JsonClass(generateAdapter = true)
data class TmdbGenreListDto(val genres: List<TmdbGenreDto> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbMovieDetailDto(
    val id: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val runtime: Int? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val homepage: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    @Json(name = "production_companies") val productionCompanies: List<TmdbCompanyDto> = emptyList(),
    /** Set only when the film is part of one; drives the collection row. */
    @Json(name = "belongs_to_collection") val belongsToCollection: TmdbCollectionRefDto? = null,
    val videos: TmdbVideosDto? = null,
    val credits: TmdbCreditsDto? = null,
    @Json(name = "watch/providers") val watchProviders: TmdbWatchProvidersDto? = null,
    val recommendations: TmdbPageDto<TmdbSummaryDto>? = null,
    val similar: TmdbPageDto<TmdbSummaryDto>? = null,
    val images: TmdbImagesDto? = null,
    @Json(name = "external_ids") val externalIds: TmdbExternalIdsDto? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetailDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val homepage: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "in_production") val inProduction: Boolean? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "last_air_date") val lastAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val networks: List<TmdbNetworkDto> = emptyList(),
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    @Json(name = "next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeDto? = null,
    val videos: TmdbVideosDto? = null,
    val credits: TmdbCreditsDto? = null,
    @Json(name = "watch/providers") val watchProviders: TmdbWatchProvidersDto? = null,
    val recommendations: TmdbPageDto<TmdbSummaryDto>? = null,
    val similar: TmdbPageDto<TmdbSummaryDto>? = null,
    val images: TmdbImagesDto? = null,
    @Json(name = "external_ids") val externalIds: TmdbExternalIdsDto? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummaryDto(
    @Json(name = "season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @Json(name = "episode_count") val episodeCount: Int = 0,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDto(
    @Json(name = "season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    @Json(name = "episode_number") val episodeNumber: Int = 0,
    @Json(name = "season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    val runtime: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbVideosDto(val results: List<TmdbVideoDto> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbVideoDto(
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto> = emptyList(),
    val crew: List<TmdbCrewDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    /** Needed to open the person's own screen; absent in the old shape of this DTO. */
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

/** Crew, filtered down to the jobs a viewer looks for — see `TmdbMappers.toCrew`. */
@JsonClass(generateAdapter = true)
data class TmdbCrewDto(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
)

/** `results` is keyed by ISO country code, e.g. `{"PK": {...}, "US": {...}}`. */
@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersDto(
    val results: Map<String, TmdbRegionProvidersDto> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class TmdbRegionProvidersDto(
    val link: String? = null,
    val flatrate: List<TmdbProviderDto> = emptyList(),
    val rent: List<TmdbProviderDto> = emptyList(),
    val buy: List<TmdbProviderDto> = emptyList(),
    val free: List<TmdbProviderDto> = emptyList(),
    val ads: List<TmdbProviderDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbProviderDto(
    @Json(name = "provider_id") val providerId: Int = 0,
    @Json(name = "provider_name") val providerName: String = "",
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "display_priority") val displayPriority: Int = 0,
)

// ----------------------------------------------------------------- images

/**
 * `movie/{id}/images` and `tv/{id}/images`.
 *
 * [logos] is the reason this endpoint is worth a call: a title's own wordmark over a
 * textless backdrop is how a hero is meant to look, and it is not available anywhere else
 * in the API.
 */
@JsonClass(generateAdapter = true)
data class TmdbImagesDto(
    val backdrops: List<TmdbImageDto> = emptyList(),
    val posters: List<TmdbImageDto> = emptyList(),
    val logos: List<TmdbImageDto> = emptyList(),
)

/** Episode image endpoints return `stills` rather than backdrops. */
@JsonClass(generateAdapter = true)
data class TmdbStillImagesDto(val stills: List<TmdbImageDto> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbImageDto(
    @Json(name = "file_path") val filePath: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @Json(name = "aspect_ratio") val aspectRatio: Double? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    /** Null for a textless image, which is what a hero overlay needs. */
    @Json(name = "iso_639_1") val language: String? = null,
)

// ------------------------------------------------------------ identifiers

/**
 * `external_ids`. The IMDb id is the one identifier other services key on, so it is what
 * a provider hand-off URL is built from when a deep link needs more than a title string.
 */
@JsonClass(generateAdapter = true)
data class TmdbExternalIdsDto(
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "tvdb_id") val tvdbId: Int? = null,
    @Json(name = "wikidata_id") val wikidataId: String? = null,
)

// ------------------------------------------------------------- collections

/** The stub on a film detail: enough to show the row heading and fetch the rest. */
@JsonClass(generateAdapter = true)
data class TmdbCollectionRefDto(
    val id: Int = 0,
    val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
)

/** `collection/{id}` — every film in the series, which the stub does not carry. */
@JsonClass(generateAdapter = true)
data class TmdbCollectionDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    val parts: List<TmdbSummaryDto> = emptyList(),
)

// ------------------------------------------- studios and broadcasters

@JsonClass(generateAdapter = true)
data class TmdbCompanyDto(
    val id: Int = 0,
    val name: String = "",
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null,
    val description: String? = null,
    val headquarters: String? = null,
    val homepage: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbNetworkDto(
    val id: Int = 0,
    val name: String = "",
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null,
    val headquarters: String? = null,
    val homepage: String? = null,
)

// ----------------------------------------------------------------- people

@JsonClass(generateAdapter = true)
data class TmdbPersonDetailDto(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    @Json(name = "known_for_department") val knownForDepartment: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @Json(name = "place_of_birth") val placeOfBirth: String? = null,
    val popularity: Double? = null,
    val homepage: String? = null,
    @Json(name = "movie_credits") val movieCredits: TmdbPersonCreditsDto? = null,
    @Json(name = "tv_credits") val tvCredits: TmdbPersonCreditsDto? = null,
    val images: TmdbPersonImagesDto? = null,
)

/**
 * A person's filmography. The entries are summaries plus the role, so they reuse
 * [TmdbSummaryDto]'s fields and add `character`/`job`.
 */
@JsonClass(generateAdapter = true)
data class TmdbPersonCreditsDto(
    val cast: List<TmdbPersonCreditDto> = emptyList(),
    val crew: List<TmdbPersonCreditDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCreditDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val character: String? = null,
    val job: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "genre_ids") val genreIds: List<Int> = emptyList(),
    /** Present on `/person/{id}/combined_credits`; absent on the typed endpoints. */
    @Json(name = "media_type") val mediaType: String? = null,
    val popularity: Double? = null,
) {
    val displayTitle: String? get() = title ?: name

    val displayDate: String? get() = releaseDate ?: firstAirDate

    /** Back to the shape the existing mappers already handle. */
    fun toSummary(): TmdbSummaryDto = TmdbSummaryDto(
        id = id,
        title = title,
        name = name,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage,
        voteCount = voteCount,
        genreIds = genreIds,
        mediaType = mediaType,
        popularity = popularity,
    )
}

@JsonClass(generateAdapter = true)
data class TmdbPersonImagesDto(val profiles: List<TmdbImageDto> = emptyList())
