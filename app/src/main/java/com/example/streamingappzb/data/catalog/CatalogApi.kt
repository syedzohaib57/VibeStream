package com.example.streamingappzb.data.catalog

import com.example.streamingappzb.data.catalog.dto.CatalogDto
import com.example.streamingappzb.data.catalog.dto.GenreDto
import com.example.streamingappzb.data.catalog.dto.TitleDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The read-only, CDN-cached v1 API from PRD §7.
 *
 * Nothing calls this while `BuildConfig.API_BASE_URL` is empty — the catalogue comes from
 * the bundled asset and Room. Pointing that field at a host is all it takes to switch;
 * the DTOs it returns are the same ones the asset parses into.
 */
interface CatalogApi {

    /** Hero plus rows, at most eight. */
    @GET("home")
    suspend fun home(@Query("kind") kind: String? = null): CatalogDto

    /** The title plus its episodes. */
    @GET("titles/{id}")
    suspend fun title(@Path("id") id: Int): TitleDto

    /** Server-side search, using the same norm and skel rules as [com.example.streamingappzb.domain.search.FuzzyMatcher]. */
    @GET("search")
    suspend fun search(@Query("q") query: String): List<TitleDto>

    @GET("genres")
    suspend fun genres(): List<GenreDto>
}
