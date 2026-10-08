package com.example.streamingappzb.data.remote.tmdb

import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCollectionDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCompanyDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCreditsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbEpisodeDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbExternalIdsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreListDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbImagesDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbMovieDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbNetworkDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPageDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonCreditsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSeasonDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbStillImagesDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbTvDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbVideosDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbWatchProvidersDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB v3 — every catalogue read in the app.
 *
 * Free for this use, but their terms carry two obligations the app has to honour rather
 * than quietly ignore: attribute TMDB as the metadata source, and attribute JustWatch for
 * the provider data. Both are on the title screen and in Settings.
 *
 * The key is a query parameter injected by [TmdbAuthInterceptor], so it never appears in
 * a call signature and cannot be forgotten at a call site.
 */
interface TmdbApi {

    // ----------------------------------------------------------------- rows

    /**
     * Trending across both types. `week` is the steady list the feed leads with; `day`
     * moves enough to be worth its own row.
     */
    @GET("trending/all/week")
    suspend fun trending(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("trending/all/day")
    suspend fun trendingToday(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("movie/popular")
    suspend fun popularMovies(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("tv/popular")
    suspend fun popularTv(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("movie/top_rated")
    suspend fun topRatedMovies(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("tv/top_rated")
    suspend fun topRatedTv(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("tv/airing_today")
    suspend fun airingToday(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("tv/on_the_air")
    suspend fun onTheAir(@Query("page") page: Int = 1): TmdbPageDto<TmdbSummaryDto>

    @GET("movie/now_playing")
    suspend fun nowPlaying(
        @Query("region") region: String,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    @GET("movie/upcoming")
    suspend fun upcomingMovies(
        @Query("region") region: String,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    // ------------------------------------------------------------- discover

    /**
     * Series with an episode airing in the window. TMDB has no "upcoming TV" endpoint, so
     * the discover query below is the equivalent.
     */
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("first_air_date.gte") firstAirDateFrom: String? = null,
        @Query("air_date.gte") airDateFrom: String? = null,
        @Query("air_date.lte") airDateTo: String? = null,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_networks") withNetworks: Int? = null,
        @Query("with_companies") withCompanies: Int? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("sort_by") sortBy: String = SORT_POPULAR,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("primary_release_date.gte") releasedFrom: String? = null,
        @Query("primary_release_date.lte") releasedTo: String? = null,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_companies") withCompanies: Int? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("sort_by") sortBy: String = SORT_POPULAR,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    /** Names for the genre ids every row endpoint returns. Cached in Room on first call. */
    @GET("genre/movie/list")
    suspend fun movieGenres(): TmdbGenreListDto

    @GET("genre/tv/list")
    suspend fun tvGenres(): TmdbGenreListDto

    // --------------------------------------------------------------- detail

    /**
     * One round trip for the whole title screen. `append_to_response` is the difference
     * between six calls and one, which matters on a slow connection.
     */
    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = APPEND_MOVIE,
    ): TmdbMovieDetailDto

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = APPEND_TV,
    ): TmdbTvDetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int,
    ): TmdbSeasonDto

    /**
     * A single episode, which is the only place TMDB reports a per-episode `runtime`. The
     * season listing omits it for a fair number of series.
     */
    @GET("tv/{id}/season/{season}/episode/{episode}")
    suspend fun episode(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int,
        @Path("episode") episodeNumber: Int,
    ): TmdbEpisodeDto

    /**
     * `imdb_id` and the social handles. The IMDb id is the one identifier other services
     * key on, so it is what a hand-off URL is built from when a provider deep link needs it.
     */
    @GET("movie/{id}/external_ids")
    suspend fun movieExternalIds(@Path("id") id: Int): TmdbExternalIdsDto

    @GET("tv/{id}/external_ids")
    suspend fun tvExternalIds(@Path("id") id: Int): TmdbExternalIdsDto

    /**
     * Recommendations, which TMDB builds from what viewers of this title actually went on
     * to watch. `similar` is keyword overlap and is noticeably worse — the reference app
     * leads with recommendations for that reason, and so does this.
     */
    @GET("movie/{id}/recommendations")
    suspend fun movieRecommendations(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    @GET("tv/{id}/recommendations")
    suspend fun tvRecommendations(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    /** Every film in a series, e.g. all of Middle-earth from any one of them. */
    @GET("collection/{id}")
    suspend fun collection(@Path("id") id: Int): TmdbCollectionDto

    // --------------------------------------------------------------- images

    /**
     * Alternative posters, backdrops and title logos.
     *
     * `include_image_language=en,null` is what makes this useful: `null` is TMDB's marker
     * for a textless image, which is the only kind a hero can safely draw its own title
     * over. Without it the response is dominated by localised posters.
     */
    @GET("movie/{id}/images")
    suspend fun movieImages(
        @Path("id") id: Int,
        @Query("include_image_language") includeImageLanguage: String = IMAGE_LANGUAGES,
    ): TmdbImagesDto

    @GET("tv/{id}/images")
    suspend fun tvImages(
        @Path("id") id: Int,
        @Query("include_image_language") includeImageLanguage: String = IMAGE_LANGUAGES,
    ): TmdbImagesDto

    @GET("tv/{id}/season/{season}/episode/{episode}/images")
    suspend fun episodeImages(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int,
        @Path("episode") episodeNumber: Int,
        @Query("include_image_language") includeImageLanguage: String = IMAGE_LANGUAGES,
    ): TmdbStillImagesDto

    // --------------------------------------------------------------- people

    @GET("person/{id}")
    suspend fun person(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = APPEND_PERSON,
    ): TmdbPersonDetailDto

    @GET("person/{id}/movie_credits")
    suspend fun personMovieCredits(@Path("id") id: Int): TmdbPersonCreditsDto

    @GET("person/{id}/tv_credits")
    suspend fun personTvCredits(@Path("id") id: Int): TmdbPersonCreditsDto

    @GET("movie/{id}/credits")
    suspend fun movieCredits(@Path("id") id: Int): TmdbCreditsDto

    @GET("tv/{id}/credits")
    suspend fun tvCredits(@Path("id") id: Int): TmdbCreditsDto

    // -------------------------------------------- studios and broadcasters

    @GET("company/{id}")
    suspend fun company(@Path("id") id: Int): TmdbCompanyDto

    @GET("network/{id}")
    suspend fun network(@Path("id") id: Int): TmdbNetworkDto

    // --------------------------------------------------------------- search

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    /**
     * Typed search, for when the caller already knows the type. `/search/multi` ranks
     * people alongside titles, which is wrong when the screen only lists one type.
     */
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPageDto<TmdbSummaryDto>

    // ------------------------------------------------------------ providers

    @GET("movie/{id}/watch/providers")
    suspend fun movieProviders(@Path("id") id: Int): TmdbWatchProvidersDto

    @GET("tv/{id}/watch/providers")
    suspend fun tvProviders(@Path("id") id: Int): TmdbWatchProvidersDto

    @GET("movie/{id}/videos")
    suspend fun movieVideos(@Path("id") id: Int): TmdbVideosDto

    @GET("tv/{id}/videos")
    suspend fun tvVideos(@Path("id") id: Int): TmdbVideosDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"

        /**
         * Everything the film title screen needs, in the one detail call.
         *
         * `recommendations` and `similar` are both appended: recommendations is better but
         * empty for obscure titles, and `similar` is the fallback that keeps the row from
         * disappearing. Costs nothing extra — appended resources are one request.
         */
        const val APPEND_MOVIE = "videos,credits,watch/providers,recommendations,similar,images,external_ids"

        const val APPEND_TV = "videos,credits,watch/providers,recommendations,similar,images,external_ids"

        /** A person's whole filmography with their detail, in one call. */
        const val APPEND_PERSON = "movie_credits,tv_credits,images"

        /** `null` is TMDB's language marker for a textless image — see [movieImages]. */
        const val IMAGE_LANGUAGES = "en,null"

        /** TMDB's genre id for Animation, used to bias anime-adjacent discovery. */
        const val GENRE_ANIMATION = "16"

        const val SORT_POPULAR = "popularity.desc"
        const val SORT_RATING = "vote_average.desc"
        const val SORT_NEWEST = "primary_release_date.desc"

        /**
         * Minimum votes for a rating-sorted query. Without it `vote_average.desc` returns
         * unknown titles with a single 10/10 vote, which is not a "top rated" list.
         */
        const val MIN_VOTES_FOR_RATING = 300
    }
}
