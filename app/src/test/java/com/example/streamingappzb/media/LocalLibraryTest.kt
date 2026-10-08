package com.example.streamingappzb.media

import com.example.streamingappzb.data.media.CompositeFreeSourceRepository
import com.example.streamingappzb.data.media.FreeSourceRepository
import com.example.streamingappzb.data.media.LocalFreeSourceRepository
import com.example.streamingappzb.data.media.LocalVideo
import com.example.streamingappzb.data.media.LocalVideoIndex
import com.example.streamingappzb.data.media.SampleFallbackSourceRepository
import com.example.streamingappzb.data.media.TitleMatch
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's own media: how a filename is read, and which file wins.
 *
 * Matching is where a personal library quietly goes wrong. A matcher that is too strict
 * offers nothing for a folder full of perfectly good films; one that is too loose plays
 * *Dune* when the viewer asked for *Dune: Part Two*. Both failures are silent, so both are
 * pinned here.
 */
class LocalLibraryTest {

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

    private fun video(
        name: String,
        sizeBytes: Long = 1_000_000_000,
        mimeType: String? = null,
    ) = LocalVideo(
        uri = "content://tree/doc/$name",
        displayName = name,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        parsed = TitleMatch.parse(name),
    )

    private fun index(vararg videos: LocalVideo) = LocalVideoIndex { videos.toList() }

    // ------------------------------------------------------------------ parsing

    @Test
    fun `a scene-named film yields its title and year`() {
        val parsed = TitleMatch.parse("The.Matrix.1999.1080p.BluRay.x264-AMIABLE.mkv")

        assertEquals("The Matrix", parsed.title)
        assertEquals(1999, parsed.year)
        assertNull(parsed.season)
        assertNull(parsed.episode)
    }

    @Test
    fun `an episode code is read and ends the title`() {
        val parsed = TitleMatch.parse("Breaking.Bad.S03E07.1080p.WEB-DL.mkv")

        assertEquals("Breaking Bad", parsed.title)
        assertEquals(3, parsed.season)
        assertEquals(7, parsed.episode)
        assertEquals("S03E07", parsed.episodeCode)
    }

    @Test
    fun `the cross notation is an episode code too`() {
        val parsed = TitleMatch.parse("Firefly - 1x02 - The Train Job.avi")

        assertEquals("Firefly", parsed.title)
        assertEquals(1, parsed.season)
        assertEquals(2, parsed.episode)
    }

    @Test
    fun `a parenthesised year is not part of the title`() {
        val parsed = TitleMatch.parse("Spirited Away (2001).mp4")

        assertEquals("Spirited Away", parsed.title)
        assertEquals(2001, parsed.year)
    }

    /**
     * A resolution is four digits and sits exactly where a year does. Reading `2160` as a
     * year would both truncate the title early and record a year that then disagrees with
     * every real title, so the file would match nothing at all.
     */
    @Test
    fun `a resolution is not mistaken for a year`() {
        val parsed = TitleMatch.parse("Arrival.2160p.HDR.mkv")

        assertEquals("Arrival", parsed.title)
        assertNull(parsed.year)
    }

    @Test
    fun `a title with no metadata at all still parses`() {
        val parsed = TitleMatch.parse("Nosferatu.mp4")

        assertEquals("Nosferatu", parsed.title)
        assertNull(parsed.year)
    }

    /** A bare year as the whole name is the title; taking it leaves nothing to match on. */
    @Test
    fun `a file named only for a year keeps it as the title`() {
        val parsed = TitleMatch.parse("1917.mkv")

        assertTrue(parsed.title.isNotBlank())
    }

    // ----------------------------------------------------------------- matching

    @Test
    fun `punctuation and separators do not prevent a match`() {
        assertTrue(
            TitleMatch.matches(
                TitleMatch.parse("Spider-Man.No.Way.Home.2021.1080p.mkv"),
                item("Spider-Man: No Way Home", 2021),
            ),
        )
    }

    @Test
    fun `a leading article is optional on either side`() {
        assertTrue(
            TitleMatch.matches(TitleMatch.parse("Matrix.1999.mkv"), item("The Matrix", 1999)),
        )
    }

    /** Off-by-one release years are routine and must not lose a real file. */
    @Test
    fun `a year one out still matches`() {
        assertTrue(
            TitleMatch.matches(TitleMatch.parse("Parasite.2020.mkv"), item("Parasite", 2019)),
        )
    }

    @Test
    fun `a year far out does not match`() {
        assertFalse(
            TitleMatch.matches(TitleMatch.parse("Crash.2004.mkv"), item("Crash", 1996)),
        )
    }

    /**
     * TMDB's year for a series is its first air date, so a season-four file legitimately
     * disagrees with it. Applying the film rule here rejected every episode after year one.
     */
    @Test
    fun `a series ignores the year entirely`() {
        assertTrue(
            TitleMatch.matches(
                TitleMatch.parse("Breaking.Bad.S05E14.2013.1080p.mkv"),
                item("Breaking Bad", 2008, MediaType.Tv),
            ),
        )
    }

    @Test
    fun `an unrelated title does not match`() {
        assertFalse(
            TitleMatch.matches(TitleMatch.parse("Interstellar.2014.mkv"), item("Inception", 2010)),
        )
    }

    /** Two letters of overlap is a coincidence, not a match. */
    @Test
    fun `a very short title does not prefix-match the library`() {
        assertFalse(
            TitleMatch.matches(TitleMatch.parse("Up.In.The.Air.2009.mkv"), item("Up", 2009)),
        )
    }

    // ------------------------------------------------------------------ picking

    /**
     * A film folder holds the feature next to a trailer and a sample, all three of which
     * match the title. Size is the only thing that tells them apart.
     */
    @Test
    fun `the largest file wins for a film`() = runTest {
        val repo = LocalFreeSourceRepository(
            index(
                video("The.Matrix.1999.trailer.mp4", sizeBytes = 8_000_000),
                video("The.Matrix.1999.1080p.mkv", sizeBytes = 9_000_000_000),
                video("The.Matrix.1999.sample.mkv", sizeBytes = 20_000_000),
            ),
        )

        val source = repo.sourceFor(item())!!

        assertTrue(source.url.endsWith("The.Matrix.1999.1080p.mkv"))
        assertEquals(StreamType.Progressive, source.type)
    }

    /** "Play" on a show means "start it", so it starts at the earliest episode on disk. */
    @Test
    fun `the earliest episode wins for a series`() = runTest {
        val repo = LocalFreeSourceRepository(
            index(
                video("Breaking.Bad.S02E01.mkv"),
                video("Breaking.Bad.S01E03.mkv"),
                video("Breaking.Bad.S01E01.mkv"),
            ),
        )

        val source = repo.sourceFor(item("Breaking Bad", 2008, MediaType.Tv))!!

        assertTrue(source.url.endsWith("Breaking.Bad.S01E01.mkv"))
        assertTrue(source.label.endsWith("S01E01"))
    }

    /** The poster said "The Matrix"; the player header must not say the filename. */
    @Test
    fun `the label is the catalogue title and the filename is the attribution`() = runTest {
        val repo = LocalFreeSourceRepository(index(video("The.Matrix.1999.1080p.BluRay.mkv")))

        val source = repo.sourceFor(item())!!

        assertEquals("The Matrix", source.label)
        assertTrue(source.attribution.contains("The.Matrix.1999.1080p.BluRay.mkv"))
    }

    /**
     * A declared type is passed through only when it says something. `octet-stream` would
     * have ExoPlayer trust a type carrying no information instead of sniffing the container.
     */
    @Test
    fun `an uninformative mime type is not what reaches the player`() = runTest {
        val matroska = LocalFreeSourceRepository(
            index(video("The.Matrix.1999.mkv", mimeType = "video/x-matroska")),
        ).sourceFor(item())!!
        assertEquals("video/x-matroska", matroska.mimeType)

        val unknown = LocalFreeSourceRepository(
            index(video("The.Matrix.1999.mkv", mimeType = null)),
        ).sourceFor(item())!!
        assertNull(unknown.mimeType)
    }

    @Test
    fun `an empty library offers nothing`() = runTest {
        assertNull(LocalFreeSourceRepository(index()).sourceFor(item()))
    }

    @Test
    fun `a local manifest is recognised as adaptive rather than progressive`() = runTest {
        val source = LocalFreeSourceRepository(index(video("The.Matrix.1999.m3u8")))
            .sourceFor(item())!!

        assertEquals(StreamType.Hls, source.type)
    }

    // ---------------------------------------------------------------- the chain

    private fun fixed(url: String) = FreeSourceRepository {
        PlayableSource(url, StreamType.Progressive, "label", "attribution")
    }

    private val none = FreeSourceRepository { null }

    private val broken = FreeSourceRepository { error("source is down") }

    @Test
    fun `the first source to answer wins`() = runTest {
        val chain = CompositeFreeSourceRepository(listOf(fixed("first"), fixed("second")))

        assertEquals("first", chain.sourceFor(item())?.url)
    }

    @Test
    fun `a source with no answer defers to the next`() = runTest {
        val chain = CompositeFreeSourceRepository(listOf(none, fixed("second")))

        assertEquals("second", chain.sourceFor(item())?.url)
    }

    /**
     * A revoked folder permission or an Archive outage must not hide a working source
     * further down the chain — which is what an unguarded `sourceFor` would do, because the
     * throw propagates out of the whole lookup.
     */
    @Test
    fun `a throwing source does not take the chain down with it`() = runTest {
        val chain = CompositeFreeSourceRepository(listOf(broken, fixed("second")))

        assertEquals("second", chain.sourceFor(item())?.url)
    }

    @Test
    fun `an exhausted chain returns nothing rather than throwing`() = runTest {
        assertNull(CompositeFreeSourceRepository(listOf(broken, none)).sourceFor(item()))
    }

    // ------------------------------------------------------------- the fallback

    @Test
    fun `the sample fallback is silent while it is switched off`() = runTest {
        assertNull(SampleFallbackSourceRepository(enabled = { false }).sourceFor(item()))
    }

    @Test
    fun `the sample fallback says it is a sample`() = runTest {
        val source = SampleFallbackSourceRepository(enabled = { true }).sourceFor(item())

        assertNotNull(source)
        assertTrue(source!!.attribution.contains("Sample", ignoreCase = true))
        assertTrue(source.attribution.contains("not the real title"))
    }

    /** A stream that changes between launches reads as a bug and voids the saved position. */
    @Test
    fun `the same title always falls back to the same sample`() = runTest {
        val repo = SampleFallbackSourceRepository(enabled = { true })

        assertEquals(repo.sourceFor(item())?.url, repo.sourceFor(item())?.url)
    }

    /** `Math.floorMod`, not `%`: a negative id would index out of the sample list. */
    @Test
    fun `a negative id does not break the fallback`() = runTest {
        val source = SampleFallbackSourceRepository(enabled = { true })
            .sourceFor(item(id = -7))

        assertNotNull(source)
    }
}
