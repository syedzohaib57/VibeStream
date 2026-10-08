package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Stream
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.search.FuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-104 and acceptance item 12: `moon lit`, `moonlight` and `monlit` must all return
 * *Moonlit Night*.
 */
class FuzzyMatcherTest {

    private fun title(
        id: Int,
        name: String,
        genre: String = "",
        cast: List<String> = emptyList(),
        kind: Kind = Kind.Drama,
    ) = Title(
        id = id,
        title = name,
        kind = kind,
        year = 2022,
        episodeCount = 10,
        runtimeMinutes = null,
        genre = genre,
        synopsis = "",
        cast = cast,
        c1 = 0,
        c2 = 0,
        motif = 0,
        artUrl = null,
        downloadable = true,
        fresh = false,
        adBreaks = emptyList(),
        stream = Stream("https://example.test/a.m3u8", StreamType.Hls),
    )

    private val moonlit = title(1, "Moonlit Night", "Romance · Family", listOf("Areeba Khalil"))
    private val palace = title(7, "Palace of Glass", "Classic", listOf("Tariq Mahmood"))
    private val river = title(8, "By the River", "Thriller", listOf("Bilal Warraich"), Kind.Film)
    private val catalogue = listOf(moonlit, palace, river)

    @Test
    fun `norm lowercases, drops non-letters and collapses runs`() {
        assertEquals("monlitnight", FuzzyMatcher.norm("Moonlit Night"))
        assertEquals("monlit", FuzzyMatcher.norm("moon lit"))
        assertEquals("monlit", FuzzyMatcher.norm("Mo-on  L!!itt"))
    }

    @Test
    fun `skel removes vowels and y from the normalised form`() {
        assertEquals("mnltnght", FuzzyMatcher.skel("Moonlit Night"))
        assertEquals("mnlght", FuzzyMatcher.skel("moonlight"))
    }

    @Test
    fun `all three spellings from the acceptance checklist find Moonlit Night`() {
        for (query in listOf("moon lit", "moonlight", "monlit")) {
            assertEquals(
                "\"$query\" should return exactly Moonlit Night",
                listOf(moonlit),
                FuzzyMatcher.filter(catalogue, query),
            )
        }
    }

    @Test
    fun `the exact title and a plain prefix both match`() {
        assertEquals(listOf(moonlit), FuzzyMatcher.filter(catalogue, "Moonlit Night"))
        assertEquals(listOf(palace), FuzzyMatcher.filter(catalogue, "palace"))
    }

    @Test
    fun `genre and cast are searchable`() {
        assertEquals(listOf(river), FuzzyMatcher.filter(catalogue, "thriller"))
        assertEquals(listOf(moonlit), FuzzyMatcher.filter(catalogue, "areeba"))
    }

    @Test
    fun `a blank query matches nothing, so Search shows browse tiles instead`() {
        assertTrue(FuzzyMatcher.filter(catalogue, "").isEmpty())
        assertTrue(FuzzyMatcher.filter(catalogue, "   ").isEmpty())
    }

    @Test
    fun `a query of only punctuation matches nothing rather than everything`() {
        // Without the design's non-empty guard this would match every title, because
        // "".contains is always true.
        assertTrue(FuzzyMatcher.filter(catalogue, "!!!").isEmpty())
        assertTrue(FuzzyMatcher.filter(catalogue, "123").isEmpty())
    }

    @Test
    fun `an unrelated query matches nothing`() {
        assertTrue(FuzzyMatcher.filter(catalogue, "zebra").isEmpty())
        assertTrue(FuzzyMatcher.filter(catalogue, "xylophone").isEmpty())
        assertTrue(FuzzyMatcher.filter(catalogue, "sputnik").isEmpty())
    }

    @Test
    fun `a single normalised letter still matches on substring, by design`() {
        // "qqqq" collapses to "q", which is genuinely inside "Tariq Mahmood". The PRD's
        // rule is substring matching with no minimum length on the normalised path, and
        // that is the behaviour a viewer typing one letter expects.
        assertEquals(listOf(palace), FuzzyMatcher.filter(catalogue, "qqqq"))
    }

    @Test
    fun `the skeleton subsequence rule stays bounded`() {
        // "mnlght" sits inside "mnltnght" with two consonants skipped — allowed.
        assertTrue(FuzzyMatcher.skeletonSubsequenceMatches("mnltnght", "mnlght"))
        // Three or more skipped is too loose to be a spelling variant.
        assertFalse(FuzzyMatcher.skeletonSubsequenceMatches("mxnxlxgxhxt", "mnlght"))
        // Under three characters is never matched on.
        assertFalse(FuzzyMatcher.skeletonSubsequenceMatches("mnltnght", "mn"))
    }
}
