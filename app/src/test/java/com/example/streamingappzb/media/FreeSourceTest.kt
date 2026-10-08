package com.example.streamingappzb.media

import com.example.streamingappzb.data.media.ArchiveFreeSourceRepository
import com.example.streamingappzb.data.remote.archive.ArchiveApi
import com.example.streamingappzb.data.remote.archive.ArchiveDocDto
import com.example.streamingappzb.data.remote.archive.ArchiveFileDto
import com.example.streamingappzb.data.remote.archive.ArchiveMetadataDto
import com.example.streamingappzb.data.remote.archive.ArchiveResponseDto
import com.example.streamingappzb.data.remote.archive.ArchiveSearchDto
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.StreamType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guardrails on what the app will play in full.
 *
 * This is the most legally sensitive code in the project: a false positive here means
 * streaming something we have no right to. Every test below is about *refusing*.
 */
class FreeSourceTest {

    private class FakeArchive(
        private val docs: List<ArchiveDocDto> = emptyList(),
        private val files: List<ArchiveFileDto> = emptyList(),
    ) : ArchiveApi {
        var searches = 0
            private set

        override suspend fun search(
            query: String,
            fields: List<String>,
            rows: Int,
            output: String,
        ): ArchiveSearchDto {
            searches++
            lastQuery = query
            return ArchiveSearchDto(ArchiveResponseDto(docs.size, docs))
        }

        override suspend fun metadata(identifier: String) = ArchiveMetadataDto(files)

        var lastQuery: String? = null
            private set
    }

    private fun item(
        title: String = "Nosferatu",
        year: Int? = 1922,
        type: MediaType = MediaType.Movie,
    ) = MediaItem(
        id = 653,
        type = type,
        title = title,
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = year,
        rating = null,
    )

    private val mp4 = ArchiveFileDto(name = "nosferatu.mp4", format = "h.264")

    @Test
    fun `a public-domain film with a matching title and a playable file is offered`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto(identifier = "nosferatu1922", title = "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "notes.txt"), mp4),
        )
        val source = ArchiveFreeSourceRepository(api).sourceFor(item())!!

        assertEquals("https://archive.org/download/nosferatu1922/nosferatu.mp4", source.url)
        assertEquals(StreamType.Progressive, source.type)
        assertTrue("attribution is not optional", source.attribution.contains("Public domain"))
    }

    @Test
    fun `a modern film is refused without even asking the Archive`() = runTest {
        val api = FakeArchive(docs = listOf(ArchiveDocDto("x", "Dune: Part Two")))
        val source = ArchiveFreeSourceRepository(api).sourceFor(item("Dune: Part Two", 2024))

        assertNull(source)
        // Not asking matters: a fuzzy match on a modern title is how these things go wrong.
        assertEquals(0, api.searches)
    }

    @Test
    fun `a title with no year is refused`() = runTest {
        val api = FakeArchive(docs = listOf(ArchiveDocDto("x", "Nosferatu")))
        assertNull(ArchiveFreeSourceRepository(api).sourceFor(item(year = null)))
        assertEquals(0, api.searches)
    }

    @Test
    fun `series and anime are never sourced this way`() = runTest {
        val api = FakeArchive(docs = listOf(ArchiveDocDto("x", "Nosferatu")))
        val repo = ArchiveFreeSourceRepository(api)
        assertNull(repo.sourceFor(item(type = MediaType.Tv)))
        assertNull(repo.sourceFor(item(type = MediaType.Anime)))
        assertEquals(0, api.searches)
    }

    @Test
    fun `a near-miss title is refused rather than played`() = runTest {
        // The Archive's search is fuzzy and will happily return a documentary *about* the
        // film. Playing that as the film would be wrong even though it is free.
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("doc", "The Making of Nosferatu")),
            files = listOf(mp4),
        )
        assertNull(ArchiveFreeSourceRepository(api).sourceFor(item()))
    }

    @Test
    fun `leading articles and punctuation do not block a genuine match`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("gold", "The Gold Rush")),
            files = listOf(ArchiveFileDto(name = "goldrush.mp4")),
        )
        val source = ArchiveFreeSourceRepository(api).sourceFor(item("Gold Rush!", 1925))
        assertTrue("normalised titles should match", source != null)
    }

    @Test
    fun `an entry with no playable file is refused`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "poster.jpg"), ArchiveFileDto(name = "info.xml")),
        )
        assertNull(ArchiveFreeSourceRepository(api).sourceFor(item()))
    }

    @Test
    fun `no results at all is refused`() = runTest {
        assertNull(ArchiveFreeSourceRepository(FakeArchive()).sourceFor(item()))
    }

    @Test
    fun `the query is scoped to films and carries no year window`() = runTest {
        val api = FakeArchive(docs = listOf(ArchiveDocDto("x", "Nosferatu")), files = listOf(mp4))
        ArchiveFreeSourceRepository(api).sourceFor(item())

        val query = api.lastQuery!!
        assertTrue(query.contains("mediatype:(movies)"))
        assertTrue(query.contains("Nosferatu"))

        // The legal guard, and the reason the loosened title test is safe: without it a
        // search for Nosferatu returned a YouTube rip, which the app would have presented
        // as public domain. Anyone can upload into opensource_movies; these are curated.
        assertTrue("must be confined to curated collections", query.contains("collection:("))
        assertTrue(query.contains("feature_films"))

        // Deliberately absent. The Archive's `year` is the *item's* year — when an upload
        // was published — not the film's release year, so a release-year window matched
        // almost nothing: verified on-device, Nosferatu resolved only once this was gone.
        //
        // No legal guard is lost. What stops a modern title being fuzzy-matched is the
        // TMDB release-year check, which refuses before any request is made — see
        // `a modern film is refused without even asking the Archive`.
        assertTrue("the Archive's year field is not the film's year", !query.contains("year:"))
    }

    @Test
    fun `an excerpt is passed over in favour of the feature`() = runTest {
        // Archive items routinely carry a trailer or title card beside the film. Picking
        // the first playable name handed the player whichever came back first.
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto(identifier = "nosferatu1922", title = "Nosferatu")),
            files = listOf(
                ArchiveFileDto(name = "trailer.mp4", format = "h.264", size = "2000000"),
                ArchiveFileDto(name = "feature.mp4", format = "h.264", size = "900000000"),
            ),
        )
        val source = ArchiveFreeSourceRepository(api).sourceFor(item())!!

        assertTrue("the feature should win, not the clip", source.url.endsWith("feature.mp4"))
    }

    @Test
    fun `an Ogg video is not offered, because Android may not decode it`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "nosferatu.ogv", format = "Ogg Video")),
        )
        // Offering it would find a source and so skip the trailer fallback, leaving the
        // viewer on a player that never renders.
        assertNull(ArchiveFreeSourceRepository(api).sourceFor(item()))
    }

    // --------------------------------------------------------------- open search
    //
    // The personal build drops the curated-collection filter and the pre-1930 ceiling,
    // which is what makes the Archive actually useful across the catalogue. What it must
    // not drop is honesty about where a file came from: the curated collections are the
    // *evidence* for the public-domain claim, so a search that does not use them cannot
    // make it.

    private fun open(api: ArchiveApi) = ArchiveFreeSourceRepository(api) { true }

    @Test
    fun `open search reaches a film the curated path refuses`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("dune2", "Dune: Part Two")),
            files = listOf(ArchiveFileDto(name = "dune2.mp4", size = "900000000")),
        )
        val source = open(api).sourceFor(item("Dune: Part Two", 2024))

        assertTrue("the year ceiling should not apply", source != null)
        assertEquals(1, api.searches)
    }

    @Test
    fun `open search does not claim an uploader-submitted file is public domain`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "n.mp4", size = "900000000")),
        )
        val source = open(api).sourceFor(item())!!

        assertTrue(source.attribution.contains("uploader-submitted"))
        assertTrue(
            "nothing here establishes the rights position the curated path can assert",
            !source.attribution.contains("Public domain"),
        )
    }

    @Test
    fun `open search drops the collection filter but stays on moving images`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "n.mp4", size = "900000000")),
        )
        open(api).sourceFor(item())

        val query = api.lastQuery!!
        assertTrue("the whole point of open search", !query.contains("collection:("))
        // Without this the same query returns texts and audio recordings sharing the name.
        assertTrue(query.contains("mediatype:(movies)"))
    }

    @Test
    fun `open search reaches television`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "ep.mp4", size = "400000000")),
        )
        assertTrue(open(api).sourceFor(item(type = MediaType.Tv)) != null)
        assertEquals(1, api.searches)
    }

    /**
     * An episode is a fraction of a feature's size, so the floor that keeps a trailer from
     * winning on a film rejects the whole of television if applied to it.
     */
    @Test
    fun `an episode is not held to the feature size floor`() = runTest {
        val tenMegabytes = ArchiveFileDto(name = "ep.mp4", size = "10000000")

        val series = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(tenMegabytes),
        )
        assertTrue(open(series).sourceFor(item(type = MediaType.Tv)) != null)

        // The same file on a film is still an excerpt, and still refused.
        val film = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(tenMegabytes),
        )
        assertNull(open(film).sourceFor(item()))
    }

    @Test
    fun `open search can offer a Matroska file`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("x", "Nosferatu")),
            files = listOf(ArchiveFileDto(name = "nosferatu.mkv", size = "900000000")),
        )
        val source = open(api).sourceFor(item())!!

        assertTrue(source.url.endsWith(".mkv"))
        // Never announced as MP4 — that is what would pick the wrong extractor.
        assertNull(source.mimeType)
    }

    /** The title test is the only guard left once collections are gone, so it still holds. */
    @Test
    fun `open search still refuses a near-miss title`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("doc", "The Making of Nosferatu")),
            files = listOf(ArchiveFileDto(name = "doc.mp4", size = "900000000")),
        )
        assertNull(open(api).sourceFor(item()))
    }

    /**
     * The prefix rule's blind spot, found on-device.
     *
     * "The Making of X" is refused because it does not *start* with the title — but
     * "X Official Trailer" does, so it sailed through and was offered under a Play button
     * as the feature. The size floor does not help: a 4K trailer is comfortably over 40 MB.
     */
    @Test
    fun `an item whose title appends 'official trailer' is not the feature`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("t", "Nosferatu Official Trailer")),
            files = listOf(ArchiveFileDto(name = "trailer.mp4", size = "900000000")),
        )
        assertNull(open(api).sourceFor(item()))
    }

    @Test
    fun `the other things-about-a-film are refused too`() = runTest {
        listOf(
            "Nosferatu - Review",
            "Nosferatu Reaction",
            "Nosferatu Behind The Scenes",
            "Nosferatu Ending Explained",
            "Nosferatu Deleted Scenes",
            "Nosferatu Soundtrack",
        ).forEach { title ->
            val api = FakeArchive(
                docs = listOf(ArchiveDocDto("x", title)),
                files = listOf(ArchiveFileDto(name = "x.mp4", size = "900000000")),
            )
            assertNull("\"$title\" is not the film", open(api).sourceFor(item()))
        }
    }

    /** The marker test must not cost a real match: a year suffix is not a marker. */
    @Test
    fun `a year or edition suffix still matches`() = runTest {
        val api = FakeArchive(
            docs = listOf(ArchiveDocDto("n", "Nosferatu (1922)")),
            files = listOf(ArchiveFileDto(name = "n.mp4", size = "900000000")),
        )
        assertTrue(ArchiveFreeSourceRepository(api).sourceFor(item()) != null)
    }
}

/**
 * Merging the two search sources.
 *
 * The property under test is that **nothing is dropped**. An earlier version advanced the
 * anime list at half speed, so when TMDB returned nothing half the anime results silently
 * disappeared — a bug invisible in any test that only checked ordering.
 */
class SearchMergeTest {

    private fun items(prefix: String, count: Int, type: MediaType) = (1..count).map {
        MediaItem(
            id = it,
            type = type,
            title = "$prefix $it",
            overview = "",
            posterPath = null,
            backdropPath = null,
            year = 2020,
            rating = null,
        )
    }

    @Test
    fun `two film results then one anime, repeating`() {
        val merged = com.example.streamingappzb.data.media.SearchMerge.interleave(
            items("Film", 4, MediaType.Movie),
            items("Anime", 2, MediaType.Anime),
        )
        assertEquals(
            listOf("Film 1", "Film 2", "Anime 1", "Film 3", "Film 4", "Anime 2"),
            merged.map { it.title },
        )
    }

    @Test
    fun `every anime survives when the film source returns nothing`() {
        val anime = items("Anime", 20, MediaType.Anime)
        val merged = com.example.streamingappzb.data.media.SearchMerge.interleave(emptyList(), anime)
        assertEquals(20, merged.size)
        assertEquals(anime.map { it.title }, merged.map { it.title })
    }

    @Test
    fun `every film survives when the anime source returns nothing`() {
        val films = items("Film", 20, MediaType.Movie)
        val merged = com.example.streamingappzb.data.media.SearchMerge.interleave(films, emptyList())
        assertEquals(20, merged.size)
    }

    @Test
    fun `an unbalanced pair still drains both lists completely`() {
        val merged = com.example.streamingappzb.data.media.SearchMerge.interleave(
            items("Film", 1, MediaType.Movie),
            items("Anime", 7, MediaType.Anime),
        )
        assertEquals(8, merged.size)
        assertEquals(7, merged.count { it.type == MediaType.Anime })
    }

    @Test
    fun `a title in both sources appears once`() {
        val shared = items("Same", 1, MediaType.Movie)
        val merged = com.example.streamingappzb.data.media.SearchMerge.interleave(shared, shared)
        assertEquals(1, merged.size)
    }

    @Test
    fun `two empty sources merge to nothing rather than throwing`() {
        assertTrue(
            com.example.streamingappzb.data.media.SearchMerge
                .interleave(emptyList(), emptyList()).isEmpty(),
        )
    }
}
