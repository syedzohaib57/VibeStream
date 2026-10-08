package com.example.streamingappzb.media

import com.example.streamingappzb.data.media.EpisodeRef
import com.example.streamingappzb.data.media.JellyfinConfig
import com.example.streamingappzb.data.media.JellyfinSourceRepository
import com.example.streamingappzb.data.remote.jellyfin.JellyfinApi
import com.example.streamingappzb.data.remote.jellyfin.JfAuthRequestDto
import com.example.streamingappzb.data.remote.jellyfin.JfAuthResponseDto
import com.example.streamingappzb.data.remote.jellyfin.JfItemDto
import com.example.streamingappzb.data.remote.jellyfin.JfItemsDto
import com.example.streamingappzb.data.remote.jellyfin.JfMediaSourceDto
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media-server source: how a catalogue title becomes an item on the viewer's own
 * Jellyfin, and what stream URL comes back.
 *
 * The matching rules are the part worth pinning. The TMDB-id join must win outright; the
 * name fallback must stay suspicious (same normalised title *and* a compatible year),
 * because a personal server full of home videos is exactly where a loose name match
 * plays the wrong thing.
 */
class JellyfinSourceTest {

    private class FakeJellyfin(
        private val byProvider: List<JfItemDto> = emptyList(),
        private val byName: List<JfItemDto> = emptyList(),
        private val episodes: List<JfItemDto> = emptyList(),
    ) : JellyfinApi {
        var providerQueries = mutableListOf<String>()
        var nameQueries = mutableListOf<String>()
        var episodeUrls = mutableListOf<String>()

        override suspend fun authenticate(
            url: String,
            authHeader: String,
            body: JfAuthRequestDto,
        ): JfAuthResponseDto = JfAuthResponseDto()

        override suspend fun itemsByProviderId(
            url: String,
            authHeader: String,
            providerId: String,
            includeTypes: String,
            recursive: Boolean,
            fields: String,
            limit: Int,
        ): JfItemsDto {
            providerQueries += providerId
            return JfItemsDto(byProvider)
        }

        override suspend fun itemsByName(
            url: String,
            authHeader: String,
            searchTerm: String,
            includeTypes: String,
            recursive: Boolean,
            fields: String,
            limit: Int,
        ): JfItemsDto {
            nameQueries += searchTerm
            return JfItemsDto(byName)
        }

        override suspend fun episodes(
            url: String,
            authHeader: String,
            season: Int?,
            fields: String,
        ): JfItemsDto {
            episodeUrls += url
            return JfItemsDto(episodes)
        }
    }

    private val config = JellyfinConfig(rawUrl = "192.168.1.5:8096", token = "tok123")

    private fun repo(api: JellyfinApi, connected: Boolean = true) =
        JellyfinSourceRepository(api) { config.takeIf { connected } }

    private fun item(
        title: String = "The Matrix",
        year: Int? = 1999,
        type: MediaType = MediaType.Movie,
        id: Int = 603,
    ) = MediaItem(
        id = id,
        type = type,
        title = title,
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = year,
        rating = null,
    )

    private fun jfMovie(
        id: String = "abc",
        name: String = "The Matrix",
        year: Int? = 1999,
    ) = JfItemDto(
        id = id,
        name = name,
        type = "Movie",
        productionYear = year,
        mediaSources = listOf(JfMediaSourceDto(id = "ms1", container = "mkv")),
    )

    private fun jfEpisode(id: String, season: Int?, episode: Int?) = JfItemDto(
        id = id,
        name = "Ep",
        type = "Episode",
        parentIndexNumber = season,
        indexNumber = episode,
        mediaSources = listOf(JfMediaSourceDto(id = "ms", container = "mkv")),
    )

    // ------------------------------------------------------------- the basics

    @Test
    fun `nothing is asked while no server is connected`() = runTest {
        val api = FakeJellyfin(byProvider = listOf(jfMovie()))
        assertNull(repo(api, connected = false).sourceFor(item()))
        assertTrue(api.providerQueries.isEmpty())
    }

    @Test
    fun `a movie resolves by its TMDB id and streams direct-play`() = runTest {
        val api = FakeJellyfin(byProvider = listOf(jfMovie(id = "m1")))
        val source = repo(api).sourceFor(item())!!

        assertEquals("Tmdb=603", api.providerQueries.single())
        assertEquals(
            "http://192.168.1.5:8096/Videos/m1/stream?static=true&api_key=tok123",
            source.url,
        )
        assertEquals("The Matrix", source.label)
        assertTrue(source.attribution.contains("192.168.1.5:8096"))
        // Container unknown until sniffed; announcing mp4 for an mkv picks the wrong extractor.
        assertNull(source.mimeType)
    }

    /** The id join hit means the name path must not run at all. */
    @Test
    fun `the id join short-circuits the name search`() = runTest {
        val api = FakeJellyfin(
            byProvider = listOf(jfMovie(id = "m1")),
            byName = listOf(jfMovie(id = "WRONG")),
        )
        val source = repo(api).sourceFor(item())!!

        assertTrue(source.url.contains("/Videos/m1/"))
        assertTrue(api.nameQueries.isEmpty())
    }

    // ---------------------------------------------------------- name fallback

    @Test
    fun `the name fallback accepts an exact title with an agreeing year`() = runTest {
        val api = FakeJellyfin(byName = listOf(jfMovie(id = "m2", year = 2000)))
        val source = repo(api).sourceFor(item(year = 1999))

        assertTrue(source != null)
        assertEquals(listOf("The Matrix"), api.nameQueries)
    }

    @Test
    fun `the name fallback refuses a year far out`() = runTest {
        // "Crash" (1996) vs "Crash" (2004): same name, different films. The year is the
        // only thing keeping a personal server from serving the wrong one.
        val api = FakeJellyfin(byName = listOf(jfMovie(name = "Crash", year = 2004)))
        assertNull(repo(api).sourceFor(item("Crash", 1996)))
    }

    @Test
    fun `the name fallback refuses a different title`() = runTest {
        val api = FakeJellyfin(byName = listOf(jfMovie(name = "The Matrix Reloaded")))
        assertNull(repo(api).sourceFor(item()))
    }

    // ----------------------------------------------------------------- series

    @Test
    fun `an episode resolves by season and episode number`() = runTest {
        val api = FakeJellyfin(
            byProvider = listOf(JfItemDto(id = "series1", name = "Breaking Bad", type = "Series")),
            episodes = listOf(
                jfEpisode("e1", season = 2, episode = 4),
                jfEpisode("e2", season = 2, episode = 5),
            ),
        )
        val source = repo(api)
            .sourceFor(item("Breaking Bad", 2008, MediaType.Tv, id = 1396), EpisodeRef(2, 5))!!

        assertTrue(source.url.contains("/Videos/e2/"))
        assertTrue("label carries the code", source.label.endsWith("S02E05"))
        assertTrue(api.episodeUrls.single().endsWith("/Shows/series1/Episodes"))
    }

    @Test
    fun `a missing episode is a miss, not a substitution`() = runTest {
        val api = FakeJellyfin(
            byProvider = listOf(JfItemDto(id = "series1", name = "Breaking Bad", type = "Series")),
            episodes = listOf(jfEpisode("e1", season = 1, episode = 1)),
        )
        // Asking for S05E09 when only S01E01 exists must NOT play S01E01 under the wrong
        // header — the chain moves on instead.
        assertNull(
            repo(api).sourceFor(item("Breaking Bad", 2008, MediaType.Tv), EpisodeRef(5, 9)),
        )
    }

    @Test
    fun `starting a show begins at the first real episode, not the specials`() = runTest {
        val api = FakeJellyfin(
            byProvider = listOf(JfItemDto(id = "series1", name = "Breaking Bad", type = "Series")),
            episodes = listOf(
                jfEpisode("sp", season = 0, episode = 1),
                jfEpisode("e2", season = 1, episode = 2),
                jfEpisode("e1", season = 1, episode = 1),
            ),
        )
        val source = repo(api).sourceFor(item("Breaking Bad", 2008, MediaType.Tv), null)!!

        assertTrue(source.url.contains("/Videos/e1/"))
    }

    /** AniList ids are not TMDB ids; joining them as TMDB would match unrelated items. */
    @Test
    fun `anime joins on the AniList provider key`() = runTest {
        val api = FakeJellyfin()
        repo(api).sourceFor(item("Frieren", 2023, MediaType.Anime, id = 154587), null)

        assertEquals("AniList=154587", api.providerQueries.single())
    }

    // ------------------------------------------------------------- normalise

    @Test
    fun `a bare host gets http and a trailing slash is dropped`() {
        assertEquals("http://192.168.1.5:8096", JellyfinConfig.normalise("192.168.1.5:8096/"))
    }

    @Test
    fun `an explicit scheme is preserved`() {
        assertEquals(
            "https://media.example.com",
            JellyfinConfig.normalise(" https://media.example.com/ "),
        )
    }

    @Test
    fun `the display host never contains the token`() {
        assertEquals("192.168.1.5:8096", config.displayHost)
    }
}
