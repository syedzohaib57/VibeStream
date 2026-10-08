package com.example.streamingappzb.media

import com.example.streamingappzb.data.media.Clock
import com.example.streamingappzb.data.remote.anilist.AniListAiringDto
import com.example.streamingappzb.data.remote.anilist.AniListCoverDto
import com.example.streamingappzb.data.remote.anilist.AniListDateDto
import com.example.streamingappzb.data.remote.anilist.AniListMappers
import com.example.streamingappzb.data.remote.anilist.AniListMediaDto
import com.example.streamingappzb.data.remote.anilist.AniListQueries
import com.example.streamingappzb.data.remote.anilist.AniListStudioDto
import com.example.streamingappzb.data.remote.anilist.AniListStudiosDto
import com.example.streamingappzb.data.remote.anilist.AniListTitleDto
import com.example.streamingappzb.data.remote.anilist.AniListTrailerDto
import com.example.streamingappzb.domain.model.BrowseQuery
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.MovieCollection
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.model.Studio
import com.example.streamingappzb.domain.model.WatchOptions
import com.example.streamingappzb.domain.repository.MediaRepository
import com.example.streamingappzb.domain.model.EpisodeSummary
import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaFeed
import com.example.streamingappzb.ui.upcoming.UpcomingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AniListMappersTest {

    private fun anime(
        english: String? = "Attack on Titan",
        romaji: String? = "Shingeki no Kyojin",
        score: Int? = 85,
        description: String? = null,
    ) = AniListMediaDto(
        id = 16498,
        title = AniListTitleDto(romaji = romaji, english = english, native = "進撃の巨人"),
        description = description,
        coverImage = AniListCoverDto(extraLarge = "https://s4.anilist.co/x.jpg"),
        seasonYear = 2013,
        startDate = AniListDateDto(2013, 4, 7),
        averageScore = score,
        episodes = 25,
        genres = listOf("Action", "Drama", "Fantasy", "Mystery"),
    )

    @Test
    fun `AniList scores out of 100 become the app's scale of ten`() {
        assertEquals(8.5, AniListMappers.toItem(anime(score = 85))!!.rating!!, 0.001)
        assertNull("an unscored title has no rating", AniListMappers.toItem(anime(score = 0))!!.rating)
        assertNull(AniListMappers.toItem(anime(score = null))!!.rating)
    }

    @Test
    fun `the English title wins, with romaji as the fallback`() {
        assertEquals("Attack on Titan", AniListMappers.toItem(anime())!!.title)
        assertEquals(
            "Shingeki no Kyojin",
            AniListMappers.toItem(anime(english = null))!!.title,
        )
        assertEquals(
            "進撃の巨人",
            AniListMappers.toItem(anime(english = null, romaji = null))!!.title,
        )
    }

    @Test
    fun `anime is its own type and carries an ISO start date`() {
        val item = AniListMappers.toItem(anime())!!
        assertEquals(MediaType.Anime, item.type)
        assertEquals("2013-04-07", item.releaseDate)
        assertEquals(2013, item.year)
        // Three genres at most: the metadata row is one line and must not wrap.
        assertEquals(3, item.genres.size)
    }

    @Test
    fun `descriptions are stripped of the HTML AniList leaves in them`() {
        val raw = "Line one.<br><br>Line <i>two</i> &amp; three. <b>Bold</b>&quot;quoted&quot;"
        assertEquals(
            "Line one.\n\nLine two & three. Bold\"quoted\"",
            AniListMappers.stripHtml(raw),
        )
        assertEquals("", AniListMappers.stripHtml(null))
    }

    @Test
    fun `a partial start date yields no ISO date rather than a wrong one`() {
        val yearOnly = anime().copy(startDate = AniListDateDto(year = 2013))
        assertNull(AniListMappers.toItem(yearOnly)!!.releaseDate)
        // The year is still known and still shown.
        assertEquals(2013, AniListMappers.toItem(yearOnly)!!.year)
    }

    @Test
    fun `detail carries the studio, the airing schedule and a YouTube trailer`() {
        val detail = AniListMappers.toDetail(
            anime().copy(
                studios = AniListStudiosDto(listOf(AniListStudioDto("Wit Studio"))),
                nextAiringEpisode = AniListAiringDto(episode = 12, airingAt = 1_700_000_000),
                trailer = AniListTrailerDto(id = "abc", site = "youtube"),
                status = "RELEASING",
            ),
            watch = WatchOptions.empty("PK"),
        )!!

        assertEquals("Studio Wit Studio", detail.tagline)
        assertEquals(12, detail.nextAiring!!.episode)
        assertEquals("abc", detail.trailers.single().key)
        assertEquals("Releasing", detail.status)
    }

    @Test
    fun `a non-YouTube trailer is dropped because there is no player for it`() {
        val detail = AniListMappers.toDetail(
            anime().copy(trailer = AniListTrailerDto(id = "123", site = "dailymotion")),
            watch = WatchOptions.empty("PK"),
        )!!
        assertTrue(detail.trailers.isEmpty())
    }

    @Test
    fun `AniList seasons are fixed three-month blocks from January`() {
        assertEquals("WINTER", AniListQueries.seasonFor(1))
        assertEquals("WINTER", AniListQueries.seasonFor(3))
        assertEquals("SPRING", AniListQueries.seasonFor(4))
        assertEquals("SUMMER", AniListQueries.seasonFor(7))
        assertEquals("FALL", AniListQueries.seasonFor(10))
        assertEquals("FALL", AniListQueries.seasonFor(12))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FixedClock(
        private val year: Int,
        private val month: Int,
        private val day: Int,
    ) : Clock {
        override fun nowMillis(): Long = 0L
        override fun todayIso(): String = "%04d-%02d-%02d".format(year, month, day)
        override fun month(): Int = month
        override fun year(): Int = year
    }

    private class FakeMedia(private val items: List<MediaItem>) : MediaRepository {
        override suspend fun feed(tab: MediaType?, forceRefresh: Boolean) = MediaFeed.EMPTY
        override fun observeFeed(tab: MediaType?): Flow<MediaFeed> = flowOf(MediaFeed.EMPTY)
        override suspend fun detail(id: Int, type: MediaType, forceRefresh: Boolean): MediaDetail? = null
        override suspend fun episodes(id: Int, type: MediaType, seasonNumber: Int): List<EpisodeSummary> =
            emptyList()

        override suspend fun search(query: String): List<MediaItem> = emptyList()
        override suspend fun upcoming(type: MediaType?): List<MediaItem> = items
        override suspend fun itemsByKeys(keys: List<String>): List<MediaItem> = emptyList()

        // The browse, person and collection reads are not exercised by Upcoming.
        override suspend fun browse(query: BrowseQuery): List<MediaItem> = emptyList()
        override suspend fun collection(id: Int): MovieCollection? = null
        override suspend fun person(id: Int): PersonProfile? = null
        override suspend fun studio(id: Int, isNetwork: Boolean): Studio? = null
        override suspend fun episode(
            id: Int,
            seasonNumber: Int,
            episodeNumber: Int,
        ): EpisodeSummary? = null
    }

    private fun item(id: Int, name: String, date: String?) = MediaItem(
        id = id,
        type = MediaType.Movie,
        title = name,
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = date?.take(4)?.toIntOrNull(),
        rating = null,
        releaseDate = date,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `releases are grouped by month, in date order`() = runTest(dispatcher) {
        val vm = UpcomingViewModel(
            FakeMedia(
                listOf(
                    item(1, "Later in March", "2026-03-20"),
                    item(2, "April", "2026-04-02"),
                    item(3, "Early March", "2026-03-04"),
                ),
            ),
            FixedClock(2026, 3, 1),
        )
        advanceUntilIdle()

        val groups = vm.state.value.groups
        assertEquals(listOf("March", "April"), groups.map { it.label })
        assertEquals(
            listOf("Early March", "Later in March"),
            groups.first().items.map { it.title },
        )
    }

    @Test
    fun `a heading from another year carries the year, the current one does not`() =
        runTest(dispatcher) {
            val vm = UpcomingViewModel(
                FakeMedia(listOf(item(1, "This year", "2026-05-01"), item(2, "Next", "2027-01-09"))),
                FixedClock(2026, 3, 1),
            )
            advanceUntilIdle()
            assertEquals(listOf("May", "January 2027"), vm.state.value.groups.map { it.label })
        }

    @Test
    fun `an unannounced date is grouped last rather than dropped`() = runTest(dispatcher) {
        val vm = UpcomingViewModel(
            FakeMedia(listOf(item(1, "No date", null), item(2, "Dated", "2026-06-01"))),
            FixedClock(2026, 3, 1),
        )
        advanceUntilIdle()

        val groups = vm.state.value.groups
        assertEquals(listOf("June", "Date to be announced"), groups.map { it.label })
        // Dropping it would hide a real, announced film.
        assertEquals("No date", groups.last().items.single().title)
    }

    @Test
    fun `nothing announced is an empty state, not a spinner forever`() = runTest(dispatcher) {
        val vm = UpcomingViewModel(FakeMedia(emptyList()), FixedClock(2026, 3, 1))
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
        assertTrue(!vm.state.value.loading)
    }
}

class MediaItemTest {

    private fun item(date: String?) = MediaItem(
        id = 1,
        type = MediaType.Movie,
        title = "A Film",
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = 2026,
        rating = 7.25,
        releaseDate = date,
    )

    @Test
    fun `upcoming is strictly after today, so a release today is out now`() {
        assertTrue(item("2026-06-01").isUpcoming("2026-05-31"))
        assertTrue(!item("2026-06-01").isUpcoming("2026-06-01"))
        assertTrue(!item("2026-06-01").isUpcoming("2026-06-02"))
        assertTrue("an unknown date is not a promise", !item(null).isUpcoming("2026-06-01"))
    }

    @Test
    fun `the key keeps a film and a series with the same id apart`() {
        val movie = item(null)
        val series = movie.copy(type = MediaType.Tv)
        assertEquals("Movie:1", movie.key)
        assertEquals("Tv:1", series.key)
        assertTrue(movie.key != series.key)
    }

    @Test
    fun `ratings show one decimal place`() {
        assertEquals("7.3", item(null).ratingOutOfTen)
        assertNull(item(null).copy(rating = 0.0).ratingOutOfTen)
    }
}
