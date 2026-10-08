package com.example.streamingappzb.media

import com.example.streamingappzb.data.remote.tmdb.TmdbImages
import com.example.streamingappzb.data.remote.tmdb.TmdbMappers
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCastDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCreditsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbMovieDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbProviderDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbRegionProvidersDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSeasonSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbTvDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbVideoDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbVideosDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbWatchProvidersDto
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.OfferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TMDB responses to domain models.
 *
 * TMDB omits keys rather than sending nulls, and omits different ones per endpoint, so
 * most of these are about what happens when a field simply is not there.
 */
class TmdbMappersTest {

    @Test
    fun `a movie summary maps with title and release date`() {
        val item = TmdbMappers.toItem(
            TmdbSummaryDto(
                id = 693134,
                title = "Dune: Part Two",
                overview = "Paul unites with the Fremen.",
                posterPath = "/poster.jpg",
                releaseDate = "2024-02-27",
                voteAverage = 8.2,
                voteCount = 4000,
                genreIds = listOf(878, 12),
            ),
            fallbackType = MediaType.Movie,
        )!!

        assertEquals(693134, item.id)
        assertEquals(MediaType.Movie, item.type)
        assertEquals("Dune: Part Two", item.title)
        assertEquals(2024, item.year)
        assertEquals(8.2, item.rating!!, 0.001)
        assertEquals(listOf("Science Fiction", "Adventure"), item.genres)
    }

    @Test
    fun `a series summary uses name and first air date`() {
        val item = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1399, name = "Game of Thrones", firstAirDate = "2011-04-17"),
            fallbackType = MediaType.Tv,
        )!!
        assertEquals("Game of Thrones", item.title)
        assertEquals(2011, item.year)
        assertEquals(MediaType.Tv, item.type)
    }

    @Test
    fun `search multi decides the type itself and drops people`() {
        val movie = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1, title = "A Film", mediaType = "movie"),
            fallbackType = null,
        )
        val series = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1, name = "A Series", mediaType = "tv"),
            fallbackType = null,
        )
        val person = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1, name = "An Actor", mediaType = "person"),
            fallbackType = null,
        )

        assertEquals(MediaType.Movie, movie!!.type)
        assertEquals(MediaType.Tv, series!!.type)
        assertNull("a person is not something you can watch", person)
    }

    @Test
    fun `an unrated title reports no rating rather than zero`() {
        val unvoted = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1, title = "Unreleased", voteAverage = 0.0, voteCount = 0),
            MediaType.Movie,
        )!!
        assertNull(unvoted.rating)
        assertNull(unvoted.ratingOutOfTen)
    }

    @Test
    fun `a summary with no usable title is dropped`() {
        assertNull(TmdbMappers.toItem(TmdbSummaryDto(id = 5), MediaType.Movie))
        assertNull(TmdbMappers.toItem(TmdbSummaryDto(id = 5, title = "  "), MediaType.Movie))
        assertNull(TmdbMappers.toItem(TmdbSummaryDto(id = 0, title = "No id"), MediaType.Movie))
    }

    @Test
    fun `movie detail carries runtime, genres and cast in billing order`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A Film",
                runtime = 112,
                releaseDate = "2020-01-01",
                genres = listOf(TmdbGenreDto(18, "Drama")),
                credits = TmdbCreditsDto(
                    cast = listOf(
                        TmdbCastDto(name = "Second", order = 1),
                        TmdbCastDto(name = "First", character = "Lead", order = 0),
                    ),
                ),
            ),
            region = "PK",
        )!!

        assertEquals(112, detail.runtimeMinutes)
        assertEquals(listOf("Drama"), detail.item.genres)
        assertEquals(listOf("First", "Second"), detail.cast.map { it.name })
        assertEquals("Lead", detail.cast.first().character)
    }

    @Test
    fun `series detail drops the specials season`() {
        val detail = TmdbMappers.toDetail(
            TmdbTvDetailDto(
                id = 1,
                name = "A Series",
                numberOfSeasons = 2,
                episodeRunTime = listOf(0, 42),
                seasons = listOf(
                    TmdbSeasonSummaryDto(seasonNumber = 0, name = "Specials", episodeCount = 3),
                    TmdbSeasonSummaryDto(seasonNumber = 1, name = "Season 1", episodeCount = 10),
                    TmdbSeasonSummaryDto(seasonNumber = 2, episodeCount = 8),
                ),
            ),
            region = "PK",
        )!!

        assertEquals(listOf(1, 2), detail.seasons.map { it.seasonNumber })
        // Season 0 is TMDB's "Specials" bucket, not part of the run.
        assertFalse(detail.seasons.any { it.name == "Specials" })
        assertEquals("Season 2", detail.seasons.last().name)
        // The first zero in episode_run_time is TMDB's placeholder, not a real runtime.
        assertEquals(42, detail.runtimeMinutes)
    }

    @Test
    fun `trailers are YouTube only, official first, trailers before teasers`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A Film",
                videos = TmdbVideosDto(
                    listOf(
                        TmdbVideoDto("v1", "Clip", "YouTube", "Clip", official = true),
                        TmdbVideoDto("v2", "Teaser", "YouTube", "Teaser", official = true),
                        TmdbVideoDto("v3", "Trailer", "YouTube", "Trailer", official = true),
                        TmdbVideoDto("v4", "Fan cut", "Vimeo", "Trailer", official = false),
                        TmdbVideoDto("v5", "Unofficial", "YouTube", "Trailer", official = false),
                    ),
                ),
            ),
            region = "US",
        )!!

        assertEquals(listOf("v3", "v2", "v1", "v5"), detail.trailers.map { it.key })
        assertEquals("v3", detail.bestTrailer!!.key)
        // Vimeo is dropped: there is no player for it, so it would be a dead button.
        assertFalse(detail.trailers.any { it.key == "v4" })
    }

    @Test
    fun `the embed url is the official IFrame player, never a media url`() {
        val trailer = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A Film",
                videos = TmdbVideosDto(listOf(TmdbVideoDto("abc123", "T", "YouTube", "Trailer"))),
            ),
            region = "US",
        )!!.trailers.single()

        assertEquals("https://www.youtube.com/embed/abc123?playsinline=1&rel=0&modestbranding=1", trailer.embedUrl)
        assertEquals("https://www.youtube.com/watch?v=abc123", trailer.watchUrl)
    }

    // ------------------------------------------------------------- providers

    private fun providers(vararg regions: Pair<String, TmdbRegionProvidersDto>) =
        TmdbWatchProvidersDto(regions.toMap())

    @Test
    fun `offers are ordered free, ads, subscription, rent, buy`() {
        val options = TmdbMappers.toWatchOptions(
            providers(
                "PK" to TmdbRegionProvidersDto(
                    link = "https://justwatch.test/x",
                    buy = listOf(TmdbProviderDto(3, "Google Play")),
                    flatrate = listOf(TmdbProviderDto(8, "Netflix")),
                    ads = listOf(TmdbProviderDto(73, "Tubi")),
                ),
            ),
            region = "PK",
        )

        assertEquals("PK", options.region)
        assertEquals(
            listOf(OfferType.Ads, OfferType.Subscription, OfferType.Buy),
            options.byType.map { it.first },
        )
        // Cheapest wins the primary button.
        assertEquals("Tubi", options.best!!.providerName)
        assertTrue(options.hasFreeOption)
        assertEquals("https://justwatch.test/x", options.justWatchLink)
    }

    @Test
    fun `an unknown region falls back to US and says so`() {
        val options = TmdbMappers.toWatchOptions(
            providers("US" to TmdbRegionProvidersDto(flatrate = listOf(TmdbProviderDto(8, "Netflix")))),
            region = "PK",
        )
        // Reporting US is the point: "nobody carries it here" and "TMDB has no data for
        // your country" look identical to a viewer, and only one of them is true.
        assertEquals("US", options.region)
        assertEquals("Netflix", options.best!!.providerName)
    }

    @Test
    fun `no provider data at all is empty, not a crash`() {
        val options = TmdbMappers.toWatchOptions(null, region = "PK")
        assertTrue(options.isEmpty)
        assertEquals("PK", options.region)
        assertNull(options.best)
        assertFalse(options.hasFreeOption)
        assertTrue(options.byType.isEmpty())
    }

    @Test
    fun `a provider listed twice in one bucket appears once`() {
        val options = TmdbMappers.toWatchOptions(
            providers(
                "GB" to TmdbRegionProvidersDto(
                    flatrate = listOf(TmdbProviderDto(8, "Netflix"), TmdbProviderDto(8, "Netflix")),
                ),
            ),
            region = "GB",
        )
        assertEquals(1, options.byType.single().second.size)
    }

    // ---------------------------------------------------------------- images

    @Test
    fun `image urls are sized for where they are drawn`() {
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", TmdbImages.poster("/p.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", TmdbImages.backdrop("b.jpg"))
        assertNull(TmdbImages.poster(null))
        assertNull(TmdbImages.poster("  "))
    }

    @Test
    fun `an absolute url passes through untouched, so AniList art works`() {
        val aniList = "https://s4.anilist.co/file/cover/large/b1-abc.jpg"
        assertEquals(aniList, TmdbImages.poster(aniList))
    }

    @Test
    fun `a malformed year is rejected rather than guessed`() {
        assertEquals(2024, TmdbMappers.yearOf("2024-02-27"))
        assertNull(TmdbMappers.yearOf(""))
        assertNull(TmdbMappers.yearOf("soon"))
        assertNull(TmdbMappers.yearOf("0001-01-01"))
        assertNull(TmdbMappers.yearOf(null))
    }
}
