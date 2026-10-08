package com.example.streamingappzb.media

import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.OfferType
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Trailer
import com.example.streamingappzb.domain.model.WatchOffer
import com.example.streamingappzb.domain.model.WatchOptions
import com.example.streamingappzb.domain.repository.RegionRepository
import com.example.streamingappzb.domain.usecase.GetWatchOptionsUseCase
import com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legal boundary, as code.
 *
 * These are the highest-value tests in the new layer: they are what stops a future change
 * from quietly making the app appear to stream a licensed title.
 */
class PlaybackAndWatchTest {

    private val resolve = ResolvePlaybackUseCase()

    private fun item(type: MediaType = MediaType.Movie) = MediaItem(
        id = 1,
        type = type,
        title = "A Film",
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = 2020,
        rating = null,
    )

    private fun detail(
        playable: PlayableSource? = null,
        offers: List<WatchOffer> = emptyList(),
        trailers: List<Trailer> = emptyList(),
        justWatch: String? = "https://justwatch.test/x",
    ) = MediaDetail(
        item = item(),
        tagline = null,
        runtimeMinutes = null,
        seasonCount = null,
        episodeCount = null,
        status = null,
        cast = emptyList(),
        trailers = trailers,
        seasons = emptyList(),
        similar = emptyList(),
        watch = WatchOptions("PK", offers, justWatch),
        playable = playable,
    )

    private val publicDomain = PlayableSource(
        url = "https://archive.org/download/x/x.mp4",
        type = StreamType.Progressive,
        label = "A Film",
        attribution = "Public domain · Internet Archive",
    )

    private val trailer = Trailer("abc", "Trailer", official = true, type = "Trailer")

    private fun offer(id: Int, name: String, type: OfferType) = WatchOffer(id, name, null, type)

    // ---------------------------------------------------------------- resolve

    @Test
    fun `a title we may serve in full plays in our own player`() {
        val action = resolve(detail(playable = publicDomain))
        assertTrue(action is ResolvePlaybackUseCase.Action.Play)
        assertEquals(
            publicDomain,
            (action as ResolvePlaybackUseCase.Action.Play).source,
        )
    }

    @Test
    fun `a licensed title hands off to its service and never invents a stream`() {
        val action = resolve(
            detail(offers = listOf(offer(8, "Netflix", OfferType.Subscription))),
        )
        assertTrue(action is ResolvePlaybackUseCase.Action.OpenProvider)
        assertEquals(
            "Netflix",
            (action as ResolvePlaybackUseCase.Action.OpenProvider).offer.providerName,
        )
    }

    @Test
    fun `a free option beats a paid one on the primary button`() {
        val action = resolve(
            detail(
                offers = listOf(
                    offer(3, "Google Play", OfferType.Buy),
                    offer(8, "Netflix", OfferType.Subscription),
                    offer(73, "Tubi", OfferType.Ads),
                ),
            ),
        ) as ResolvePlaybackUseCase.Action.OpenProvider

        assertEquals("Tubi", action.offer.providerName)
        assertTrue(action.offer.offerType.costsNothing)
    }

    @Test
    fun `a playable source outranks even a free service`() {
        // Ours to serve beats sending the viewer elsewhere.
        val action = resolve(
            detail(playable = publicDomain, offers = listOf(offer(73, "Tubi", OfferType.Ads))),
        )
        assertTrue(action is ResolvePlaybackUseCase.Action.Play)
    }

    @Test
    fun `with nowhere to watch it offers the trailer`() {
        val action = resolve(detail(trailers = listOf(trailer)))
        assertTrue(action is ResolvePlaybackUseCase.Action.PlayTrailer)
    }

    @Test
    fun `with nothing at all it says so instead of pretending`() {
        assertEquals(ResolvePlaybackUseCase.Action.Unavailable, resolve(detail()))
    }

    @Test
    fun `the secondary action never repeats the primary one`() {
        val withBoth = detail(
            offers = listOf(offer(8, "Netflix", OfferType.Subscription)),
            trailers = listOf(trailer),
        )
        val primary = resolve(withBoth)
        assertTrue(resolve.secondary(withBoth, primary) is ResolvePlaybackUseCase.Action.PlayTrailer)

        // When the trailer *is* the primary, there is no secondary to offer.
        val trailerOnly = detail(trailers = listOf(trailer))
        assertNull(resolve.secondary(trailerOnly, resolve(trailerOnly)))

        val nothing = detail()
        assertNull(resolve.secondary(nothing, resolve(nothing)))
    }

    // --------------------------------------------------------------- hand-off

    private class FakeRegion(private val code: String) : RegionRepository {
        override fun region() = code
        override fun setRegion(code: String) = Unit
        override fun deviceRegion() = code
    }

    private val handoff = GetWatchOptionsUseCase(FakeRegion("PK"))

    @Test
    fun `an installed provider app is launched directly`() {
        val result = handoff(
            offer = offer(8, "Netflix", OfferType.Subscription),
            title = "Dune: Part Two",
            justWatchLink = "https://justwatch.test/x",
            isInstalled = { it == "com.netflix.mediaclient" },
        )
        assertEquals("com.netflix.mediaclient", result.packageName)
        // The web URL is still filled in: it is the fallback if the launch fails.
        assertTrue(result.url.startsWith("https://www.netflix.com/search?q="))
    }

    @Test
    fun `a missing app falls back to the service's own web search`() {
        val result = handoff(
            offer = offer(8, "Netflix", OfferType.Subscription),
            title = "Dune: Part Two",
            justWatchLink = null,
            isInstalled = { false },
        )
        assertNull(result.packageName)
        assertEquals("https://www.netflix.com/search?q=Dune%3A%20Part%20Two", result.url)
    }

    @Test
    fun `a provider we have no entry for falls back to JustWatch`() {
        val result = handoff(
            offer = offer(999_999, "Some Regional Service", OfferType.Subscription),
            title = "A Film",
            justWatchLink = "https://justwatch.test/title",
            isInstalled = { true },
        )
        assertNull(result.packageName)
        assertEquals("https://justwatch.test/title", result.url)
    }

    @Test
    fun `an unknown provider with no JustWatch link still goes somewhere useful`() {
        val result = handoff(
            offer = offer(999_999, "Some Service", OfferType.Subscription),
            title = "A Film",
            justWatchLink = null,
            isInstalled = { true },
        )
        assertTrue(result.url.startsWith("https://"))
    }

    @Test
    fun `titles are url encoded with spaces, not plus signs`() {
        val result = handoff(
            offer = offer(283, "Crunchyroll", OfferType.Subscription),
            title = "Attack on Titan",
            justWatchLink = null,
            isInstalled = { false },
        )
        // A literal '+' in a path segment is a plus, not a space — %20 is unambiguous.
        assertEquals("https://www.crunchyroll.com/search?q=Attack%20on%20Titan", result.url)
    }

    @Test
    fun `South Asian services are reachable, not just the American ones`() {
        for ((id, name) in listOf(122 to "Hotstar", 232 to "Zee5", 237 to "SonyLIV")) {
            val result = handoff(
                offer = offer(id, name, OfferType.Subscription),
                title = "A Film",
                justWatchLink = null,
                isInstalled = { false },
            )
            assertTrue("$name should have its own destination", result.url.contains("%20") || result.url.contains("A"))
            assertTrue("$name should not be a Google fallback", !result.url.contains("google.com/search"))
        }
    }
}
