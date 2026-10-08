package com.example.streamingappzb.media

import com.example.streamingappzb.data.remote.tmdb.TmdbGenres
import com.example.streamingappzb.data.remote.tmdb.TmdbMappers
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCollectionDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCollectionRefDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCompanyDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCreditsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbCrewDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbExternalIdsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbGenreListDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbImageDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbImagesDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbMovieDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbNetworkDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPageDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonCreditDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonCreditsDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonDetailDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbSummaryDto
import com.example.streamingappzb.data.remote.tmdb.dto.TmdbTvDetailDto
import com.example.streamingappzb.domain.model.GenreScope
import com.example.streamingappzb.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The endpoints brought over from the reference app: genres, images, people, collections,
 * crew and recommendations.
 *
 * Several of these encode a judgement rather than a transformation — which backdrop is
 * usable, which crew jobs are worth billing, recommendations over `similar` — so the tests
 * are about those decisions and not just field copying.
 */
class TmdbBrowseMappersTest {

    // ----------------------------------------------------------------- genres

    @Test
    fun `genre lists map with their scope`() {
        val movies = TmdbMappers.toGenres(
            TmdbGenreListDto(listOf(TmdbGenreDto(878, "Science Fiction"))),
            GenreScope.Movie,
        )
        val series = TmdbMappers.toGenres(
            TmdbGenreListDto(listOf(TmdbGenreDto(10765, "Sci-Fi & Fantasy"))),
            GenreScope.Tv,
        )

        assertEquals("Movie:878", movies.single().key)
        assertEquals("Tv:10765", series.single().key)
        assertEquals(MediaType.Tv, series.single().scope.mediaType)
    }

    @Test
    fun `a blank or zero-id genre is dropped rather than shown empty`() {
        val mapped = TmdbMappers.toGenres(
            TmdbGenreListDto(
                listOf(
                    TmdbGenreDto(28, "Action"),
                    TmdbGenreDto(0, "Bad id"),
                    TmdbGenreDto(99, ""),
                ),
            ),
            GenreScope.Movie,
        )

        assertEquals(listOf("Action"), mapped.map { it.name })
    }

    @Test
    fun `the built-in fallback keeps the two id spaces apart`() {
        val movies = TmdbGenres.fallback(GenreScope.Movie)
        val series = TmdbGenres.fallback(GenreScope.Tv)

        // 10765 is Sci-Fi & Fantasy and exists only for television.
        assertTrue(series.any { it.id == 10765 })
        assertFalse(movies.any { it.id == 10765 })
        // 16 is Animation in both spaces.
        assertTrue(movies.any { it.id == 16 })
        assertTrue(series.any { it.id == 16 })
    }

    @Test
    fun `an injected namer overrides the built-in list`() {
        val item = TmdbMappers.toItem(
            TmdbSummaryDto(id = 1, title = "A film", genreIds = listOf(878)),
            fallbackType = MediaType.Movie,
            genreNames = { ids -> ids.map { "live-$it" } },
        )!!

        assertEquals(listOf("live-878"), item.genres)
    }

    // ----------------------------------------------------------------- images

    @Test
    fun `only textless backdrops survive, because a hero draws its own title`() {
        val images = TmdbMappers.toImages(
            TmdbImagesDto(
                backdrops = listOf(
                    TmdbImageDto(filePath = "/clean.jpg", language = null, voteAverage = 5.0),
                    TmdbImageDto(filePath = "/english-title.jpg", language = "en", voteAverage = 9.0),
                ),
            ),
        )

        assertEquals(listOf("/clean.jpg"), images.backdrops)
    }

    @Test
    fun `the best-rated non-SVG logo wins`() {
        val images = TmdbMappers.toImages(
            TmdbImagesDto(
                logos = listOf(
                    TmdbImageDto(filePath = "/best.svg", voteAverage = 9.9),
                    TmdbImageDto(filePath = "/good.png", voteAverage = 7.0),
                    TmdbImageDto(filePath = "/worse.png", voteAverage = 2.0),
                ),
            ),
        )

        // The SVG is better rated but Coil needs an extra decoder for it.
        assertEquals("/good.png", images.logoPath)
    }

    @Test
    fun `a missing images block is empty rather than null`() {
        assertTrue(TmdbMappers.toImages(null).isEmpty)
    }

    // ------------------------------------------------------------------ crew

    @Test
    fun `crew is narrowed to billed jobs, directors first`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                credits = TmdbCreditsDto(
                    crew = listOf(
                        TmdbCrewDto(id = 3, name = "A Gaffer", job = "Gaffer"),
                        TmdbCrewDto(id = 2, name = "A Writer", job = "Screenplay"),
                        TmdbCrewDto(id = 1, name = "A Director", job = "Director"),
                    ),
                ),
            ),
            region = "US",
        )!!

        assertEquals(listOf("A Director", "A Writer"), detail.crew.map { it.name })
        assertEquals(listOf("A Director"), detail.directors.map { it.name })
    }

    @Test
    fun `the same person twice on one film appears once per job, not per entry`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                credits = TmdbCreditsDto(
                    crew = listOf(
                        TmdbCrewDto(id = 1, name = "Auteur", job = "Director"),
                        TmdbCrewDto(id = 1, name = "Auteur", job = "Director"),
                        TmdbCrewDto(id = 1, name = "Auteur", job = "Screenplay"),
                    ),
                ),
            ),
            region = "US",
        )!!

        assertEquals(listOf("Director", "Screenplay"), detail.crew.map { it.job })
    }

    @Test
    fun `cast carries the person id so a face can be opened`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                credits = TmdbCreditsDto(
                    cast = listOf(
                        com.example.streamingappzb.data.remote.tmdb.dto.TmdbCastDto(
                            id = 1245,
                            name = "Scarlett Johansson",
                            character = "Natasha",
                        ),
                    ),
                ),
            ),
            region = "US",
        )!!

        assertEquals(1245, detail.cast.single().id)
    }

    // -------------------------------------------------------- recommendations

    @Test
    fun `related prefers recommendations over similar`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                recommendations = TmdbPageDto(
                    results = listOf(TmdbSummaryDto(id = 10, title = "Recommended")),
                ),
                similar = TmdbPageDto(results = listOf(TmdbSummaryDto(id = 20, title = "Similar"))),
            ),
            region = "US",
        )!!

        assertEquals(listOf("Recommended"), detail.related.map { it.title })
    }

    @Test
    fun `related falls back to similar when there are no recommendations`() {
        val detail = TmdbMappers.toDetail(
            TmdbTvDetailDto(
                id = 1,
                name = "A series",
                recommendations = TmdbPageDto(results = emptyList()),
                similar = TmdbPageDto(results = listOf(TmdbSummaryDto(id = 20, name = "Similar"))),
            ),
            region = "US",
        )!!

        assertEquals(listOf("Similar"), detail.related.map { it.title })
    }

    // ------------------------------------------------------------ collections

    @Test
    fun `a collection is ordered by release date, not by the API's order`() {
        val collection = TmdbMappers.toCollection(
            TmdbCollectionDto(
                id = 119,
                name = "The Lord of the Rings Collection",
                parts = listOf(
                    TmdbSummaryDto(id = 122, title = "The Return of the King", releaseDate = "2003-12-01"),
                    TmdbSummaryDto(id = 120, title = "The Fellowship of the Ring", releaseDate = "2001-12-18"),
                    TmdbSummaryDto(id = 121, title = "The Two Towers", releaseDate = "2002-12-18"),
                ),
            ),
        )!!

        assertEquals(
            listOf("The Fellowship of the Ring", "The Two Towers", "The Return of the King"),
            collection.parts.map { it.title },
        )
        assertTrue(collection.isWorthShowing)
    }

    @Test
    fun `a collection of one is not worth a row`() {
        val collection = TmdbMappers.toCollection(
            TmdbCollectionDto(
                id = 1,
                name = "Announced Series",
                parts = listOf(TmdbSummaryDto(id = 1, title = "The only film")),
            ),
        )!!

        assertFalse(collection.isWorthShowing)
    }

    @Test
    fun `a film's collection stub reaches the detail`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 120,
                title = "The Fellowship of the Ring",
                belongsToCollection = TmdbCollectionRefDto(id = 119, name = "The Lord of the Rings"),
            ),
            region = "US",
        )!!

        assertEquals(119, detail.collectionId)
        assertEquals("The Lord of the Rings", detail.collectionName)
    }

    @Test
    fun `a film in no collection has no collection id`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(id = 1, title = "A standalone film"),
            region = "US",
        )!!

        assertNull(detail.collectionId)
    }

    // ----------------------------------------------------------------- people

    @Test
    fun `a filmography merges both spaces, newest first, deduped`() {
        val person = TmdbMappers.toPerson(
            TmdbPersonDetailDto(
                id = 1283,
                name = "Helena Bonham Carter",
                knownForDepartment = "Acting",
                movieCredits = TmdbPersonCreditsDto(
                    cast = listOf(
                        TmdbPersonCreditDto(
                            id = 10,
                            title = "Older film",
                            releaseDate = "1999-01-01",
                            character = "Marla",
                        ),
                        TmdbPersonCreditDto(
                            id = 11,
                            title = "Newer film",
                            releaseDate = "2010-01-01",
                            character = "The Red Queen",
                        ),
                    ),
                ),
                tvCredits = TmdbPersonCreditsDto(
                    cast = listOf(
                        TmdbPersonCreditDto(
                            id = 12,
                            name = "A series",
                            firstAirDate = "2019-01-01",
                            character = "Princess Margaret",
                        ),
                    ),
                ),
            ),
        )!!

        assertEquals(
            listOf("A series", "Newer film", "Older film"),
            person.primaryCredits.map { it.item.title },
        )
        // A series id and a film id of the same number must not collide.
        assertEquals(
            listOf(MediaType.Tv, MediaType.Movie, MediaType.Movie),
            person.primaryCredits.map { it.item.type },
        )
        assertEquals("Princess Margaret", person.primaryCredits.first().role)
    }

    @Test
    fun `a director's page leads with what they directed`() {
        val person = TmdbMappers.toPerson(
            TmdbPersonDetailDto(
                id = 1,
                name = "A Director",
                knownForDepartment = "Directing",
                movieCredits = TmdbPersonCreditsDto(
                    cast = listOf(
                        TmdbPersonCreditDto(id = 10, title = "A cameo", releaseDate = "2000-01-01"),
                    ),
                    crew = listOf(
                        TmdbPersonCreditDto(
                            id = 11,
                            title = "Their film",
                            releaseDate = "2005-01-01",
                            job = "Director",
                        ),
                    ),
                ),
            ),
        )!!

        assertEquals(listOf("Their film"), person.primaryCredits.map { it.item.title })
        assertEquals("Director", person.primaryCredits.single().role)
    }

    @Test
    fun `an undated credit sorts above a dated one, being unreleased`() {
        val person = TmdbMappers.toPerson(
            TmdbPersonDetailDto(
                id = 1,
                name = "A person",
                knownForDepartment = "Acting",
                movieCredits = TmdbPersonCreditsDto(
                    cast = listOf(
                        TmdbPersonCreditDto(id = 10, title = "Released", releaseDate = "2020-01-01"),
                        TmdbPersonCreditDto(id = 11, title = "Announced"),
                    ),
                ),
            ),
        )!!

        assertEquals(listOf("Announced", "Released"), person.primaryCredits.map { it.item.title })
    }

    @Test
    fun `lifespan reads as a range once there is a death date`() {
        val living = TmdbMappers.toPerson(
            TmdbPersonDetailDto(id = 1, name = "A", birthday = "1949-10-08"),
        )!!
        val died = TmdbMappers.toPerson(
            TmdbPersonDetailDto(id = 2, name = "B", birthday = "1922-01-01", deathday = "2001-02-03"),
        )!!
        val unknown = TmdbMappers.toPerson(TmdbPersonDetailDto(id = 3, name = "C"))!!

        assertEquals("1949", living.lifespan)
        assertEquals("1922–2001", died.lifespan)
        assertNull(unknown.lifespan)
    }

    @Test
    fun `a person with no photo falls back to their first profile image`() {
        val person = TmdbMappers.toPerson(
            TmdbPersonDetailDto(
                id = 1,
                name = "A person",
                profilePath = null,
                images = com.example.streamingappzb.data.remote.tmdb.dto.TmdbPersonImagesDto(
                    profiles = listOf(TmdbImageDto(filePath = "/fallback.jpg")),
                ),
            ),
        )!!

        assertEquals("/fallback.jpg", person.profilePath)
    }

    // ----------------------------------------------- studios and identifiers

    @Test
    fun `companies and networks map to the same type with isNetwork set`() {
        val company = TmdbMappers.toStudio(TmdbCompanyDto(id = 1, name = "Legendary"))
        val network = TmdbMappers.toStudio(TmdbNetworkDto(id = 49, name = "HBO"))

        assertFalse(company.isNetwork)
        assertTrue(network.isNetwork)
    }

    @Test
    fun `a series takes its studios from networks, a film from production companies`() {
        val film = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                productionCompanies = listOf(TmdbCompanyDto(id = 1, name = "Legendary")),
            ),
            region = "US",
        )!!
        val series = TmdbMappers.toDetail(
            TmdbTvDetailDto(
                id = 1,
                name = "A series",
                networks = listOf(TmdbNetworkDto(id = 49, name = "HBO")),
            ),
            region = "US",
        )!!

        assertEquals(listOf("Legendary"), film.studios.map { it.name })
        assertFalse(film.studios.single().isNetwork)
        assertEquals(listOf("HBO"), series.studios.map { it.name })
        assertTrue(series.studios.single().isNetwork)
    }

    @Test
    fun `the imdb id reaches the detail for a provider hand-off`() {
        val detail = TmdbMappers.toDetail(
            TmdbMovieDetailDto(
                id = 1,
                title = "A film",
                externalIds = TmdbExternalIdsDto(imdbId = "tt0120737"),
            ),
            region = "US",
        )!!

        assertEquals("tt0120737", detail.imdbId)
    }

    @Test
    fun `an original title is only offered when it differs from the display title`() {
        val translated = TmdbMappers.toDetail(
            TmdbMovieDetailDto(id = 1, title = "Parasite", originalTitle = "기생충"),
            region = "US",
        )!!
        val same = TmdbMappers.toDetail(
            TmdbMovieDetailDto(id = 2, title = "Dune", originalTitle = "Dune"),
            region = "US",
        )!!

        assertEquals("기생충", translated.alternateTitle)
        assertNull(same.alternateTitle)
    }
}
