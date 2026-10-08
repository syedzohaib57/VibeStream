package com.example.streamingappzb.data.remote.archive

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Internet Archive's search and metadata endpoints.
 *
 * Public, keyless, and the only source in this app that serves full video legally. Used
 * solely to find public-domain films — see
 * [com.example.streamingappzb.data.media.ArchiveFreeSourceRepository].
 */
interface ArchiveApi {

    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = DEFAULT_FIELDS,
        @Query("rows") rows: Int = 5,
        @Query("output") output: String = "json",
    ): ArchiveSearchDto

    @GET("metadata/{identifier}")
    suspend fun metadata(@Path("identifier") identifier: String): ArchiveMetadataDto

    companion object {
        const val BASE_URL = "https://archive.org/"
        val DEFAULT_FIELDS = listOf("identifier", "title", "year", "mediatype")
    }
}

@JsonClass(generateAdapter = true)
data class ArchiveSearchDto(val response: ArchiveResponseDto? = null)

@JsonClass(generateAdapter = true)
data class ArchiveResponseDto(
    @Json(name = "numFound") val numFound: Int = 0,
    val docs: List<ArchiveDocDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArchiveDocDto(
    val identifier: String = "",
    val title: String? = null,
    val year: String? = null,
    val mediatype: String? = null,
)

@JsonClass(generateAdapter = true)
data class ArchiveMetadataDto(
    val files: List<ArchiveFileDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArchiveFileDto(
    val name: String? = null,
    val format: String? = null,
    val size: String? = null,
)
