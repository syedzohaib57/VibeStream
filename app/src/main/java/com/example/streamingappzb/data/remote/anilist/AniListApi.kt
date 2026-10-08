package com.example.streamingappzb.data.remote.anilist

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AniList's public GraphQL API.
 *
 * Chosen over Jikan for anime because it is rate-limited far more generously, returns the
 * airing schedule (`nextAiringEpisode`) that a "what's on this season" row needs, and
 * needs no key or account at all.
 *
 * One POST endpoint; the query is the payload. Rather than pull in a GraphQL client and
 * its codegen for four queries, the queries are string constants and the responses are
 * ordinary Moshi DTOs — the shapes are small and stable.
 */
interface AniListApi {

    @POST(".")
    suspend fun query(@Body request: AniListRequest): AniListResponse

    companion object {
        const val BASE_URL = "https://graphql.anilist.co/"
    }
}

@JsonClass(generateAdapter = true)
data class AniListRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class AniListResponse(val data: AniListData? = null)

@JsonClass(generateAdapter = true)
data class AniListData(
    @Json(name = "Page") val page: AniListPage? = null,
    @Json(name = "Media") val media: AniListMediaDto? = null,
)

@JsonClass(generateAdapter = true)
data class AniListPage(val media: List<AniListMediaDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListMediaDto(
    val id: Int = 0,
    val title: AniListTitleDto? = null,
    val description: String? = null,
    val coverImage: AniListCoverDto? = null,
    val bannerImage: String? = null,
    val seasonYear: Int? = null,
    val startDate: AniListDateDto? = null,
    /** 0..100 on AniList; converted to the 0..10 the rest of the app uses. */
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val format: String? = null,
    val studios: AniListStudiosDto? = null,
    val nextAiringEpisode: AniListAiringDto? = null,
    val trailer: AniListTrailerDto? = null,
    val characters: AniListCharactersDto? = null,
)

@JsonClass(generateAdapter = true)
data class AniListTitleDto(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    /** English where it exists — most viewers search the English title. */
    val display: String? get() = english?.takeIf { it.isNotBlank() } ?: romaji ?: native
}

@JsonClass(generateAdapter = true)
data class AniListCoverDto(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
) {
    val best: String? get() = extraLarge ?: large ?: medium
}

@JsonClass(generateAdapter = true)
data class AniListDateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    /** `yyyy-MM-dd`, or null when AniList only knows the year. */
    val iso: String?
        get() {
            val y = year ?: return null
            val m = month ?: return null
            val d = day ?: return null
            return "%04d-%02d-%02d".format(y, m, d)
        }
}

@JsonClass(generateAdapter = true)
data class AniListStudiosDto(val nodes: List<AniListStudioDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListStudioDto(val name: String = "")

@JsonClass(generateAdapter = true)
data class AniListAiringDto(
    val episode: Int = 0,
    val airingAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class AniListTrailerDto(
    val id: String? = null,
    val site: String? = null,
)

@JsonClass(generateAdapter = true)
data class AniListCharactersDto(val nodes: List<AniListCharacterDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListCharacterDto(
    val name: AniListCharacterNameDto? = null,
    val image: AniListCoverDto? = null,
)

@JsonClass(generateAdapter = true)
data class AniListCharacterNameDto(val full: String? = null)
