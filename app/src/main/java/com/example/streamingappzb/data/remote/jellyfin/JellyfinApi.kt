package com.example.streamingappzb.data.remote.jellyfin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The small slice of Jellyfin's API this app needs: sign in, find an item, stream it.
 *
 * Every call takes a full [Url] rather than relying on a Retrofit base URL, because the
 * server address is the viewer's own and arrives at runtime from the library sheet — a
 * Retrofit instance is built once at startup, long before the address is known.
 *
 * Authentication is Jellyfin's own scheme, not OAuth: one POST with the username and
 * password returns a long-lived access token, which then rides every request in the
 * `Authorization: MediaBrowser ...` header. The `Client`/`Device`/`Version` fields in
 * that header are mandatory — the server rejects the authentication POST without them —
 * and they are also what names this app in the server's Devices dashboard.
 */
interface JellyfinApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: JfAuthRequestDto,
    ): JfAuthResponseDto

    /**
     * Items filtered by TMDB id — the precise join.
     *
     * `AnyProviderIdEquals` matches against the provider ids Jellyfin itself scraped when
     * it indexed the library, so a hit here *is* the catalogue title, not a guess. Both
     * `Recursive` and the type filter are required: without them the query returns
     * folders and collections alongside the media itself.
     */
    @GET
    suspend fun itemsByProviderId(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("AnyProviderIdEquals") providerId: String,
        @Query("IncludeItemTypes") includeTypes: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = FIELDS,
        @Query("Limit") limit: Int = SEARCH_LIMIT,
    ): JfItemsDto

    /** Items by free-text search — the fallback when the server has no TMDB id. */
    @GET
    suspend fun itemsByName(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("SearchTerm") searchTerm: String,
        @Query("IncludeItemTypes") includeTypes: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = FIELDS,
        @Query("Limit") limit: Int = SEARCH_LIMIT,
    ): JfItemsDto

    /**
     * The episodes of a series, optionally narrowed to one season. Jellyfin numbers
     * both exactly as TMDB does, so (season, episode) joins cleanly.
     */
    @GET
    suspend fun episodes(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Query("Season") season: Int? = null,
        @Query("Fields") fields: String = FIELDS,
    ): JfItemsDto

    companion object {
        /** `ProviderIds` is the TMDB join; `MediaSources` says whether it can play. */
        const val FIELDS = "ProviderIds,MediaSources,Path"

        const val SEARCH_LIMIT = 10

        const val TYPE_MOVIE = "Movie"
        const val TYPE_SERIES = "Series"
    }
}

@JsonClass(generateAdapter = true)
data class JfAuthRequestDto(
    @Json(name = "Username") val username: String,
    @Json(name = "Pw") val password: String,
)

@JsonClass(generateAdapter = true)
data class JfAuthResponseDto(
    @Json(name = "AccessToken") val accessToken: String? = null,
    @Json(name = "User") val user: JfUserDto? = null,
)

@JsonClass(generateAdapter = true)
data class JfUserDto(
    @Json(name = "Id") val id: String? = null,
    @Json(name = "Name") val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class JfItemsDto(
    @Json(name = "Items") val items: List<JfItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class JfItemDto(
    @Json(name = "Id") val id: String = "",
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "ProductionYear") val productionYear: Int? = null,
    @Json(name = "IndexNumber") val indexNumber: Int? = null,
    @Json(name = "ParentIndexNumber") val parentIndexNumber: Int? = null,
    @Json(name = "ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @Json(name = "MediaSources") val mediaSources: List<JfMediaSourceDto> = emptyList(),
) {
    /** Jellyfin's provider map capitalises inconsistently across versions; read both. */
    val tmdbId: Int?
        get() = (providerIds["Tmdb"] ?: providerIds["tmdb"])?.toIntOrNull()

    val hasPlayableSource: Boolean get() = mediaSources.isNotEmpty()
}

@JsonClass(generateAdapter = true)
data class JfMediaSourceDto(
    @Json(name = "Id") val id: String? = null,
    @Json(name = "Container") val container: String? = null,
)
