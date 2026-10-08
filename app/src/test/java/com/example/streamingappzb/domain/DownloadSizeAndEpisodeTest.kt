package com.example.streamingappzb.domain

import com.example.streamingappzb.data.catalog.EpisodeFactory
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.megabytesFor
import com.example.streamingappzb.domain.usecase.EstimateDownloadSizeUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-302: the size the download sheet shows before anything starts, and the episode
 * durations it is computed from.
 */
class DownloadSizeAndEpisodeTest {

    private val catalog = FakeCatalogRepository()
    private val estimate = EstimateDownloadSizeUseCase(catalog)

    @Test
    fun `episode durations match the design's own formula`() {
        // Catalog.jsx: dur = (38 + ((i * 7) % 9)) * 60 + 10
        assertEquals(38 * 60 + 10, EpisodeFactory.durationSecondsFor(0))
        assertEquals(45 * 60 + 10, EpisodeFactory.durationSecondsFor(1))
        assertEquals(43 * 60 + 10, EpisodeFactory.durationSecondsFor(2))
        assertEquals(41 * 60 + 10, EpisodeFactory.durationSecondsFor(3))
        // Every episode lands between 38 and 46 minutes.
        for (i in 0 until 30) {
            val minutes = EpisodeFactory.durationSecondsFor(i) / 60
            assertTrue("episode $i was $minutes min", minutes in 38..46)
        }
    }

    @Test
    fun `a film is one episode named after the title`() {
        val factory = EpisodeFactory(listOf("First Step"))
        val episodes = factory.episodesFor(FakeCatalogRepository.FILM)
        assertEquals(1, episodes.size)
        assertEquals("Blue Tide", episodes.single().name)
        assertEquals(124 * 60, episodes.single().durationSeconds)
    }

    @Test
    fun `megabytes is rung times runtime over an hour`() {
        val rung240 = Rung.LADDER.single { it.id == Rung.ID_240P }
        // A full hour at 155 MB/hr is 155 MB.
        assertEquals(155.0, rung240.megabytesFor(3600), 0.001)
        // Half an hour is half the cost.
        assertEquals(77.5, rung240.megabytesFor(1800), 0.001)
        assertEquals(0.0, rung240.megabytesFor(0), 0.001)
    }

    @Test
    fun `the sheet offers exactly the three rungs the PRD specifies`() = runTest {
        val options = estimate.options(titleId = 1, episode = 1, selectedRungId = Rung.ID_240P)
        assertEquals(
            listOf(Rung.ID_240P, Rung.ID_360P, Rung.ID_480P),
            options.map { it.rung.id },
        )
        assertEquals(1, options.count { it.selected })
    }

    @Test
    fun `each rung's size rises with its bitrate`() = runTest {
        val options = estimate.options(titleId = 1, episode = 1, selectedRungId = Rung.ID_240P)
        val sizes = options.map { it.megabytes }
        assertEquals(sizes.sorted(), sizes)
        assertTrue("sizes must be non-zero", sizes.all { it > 0.0 })
    }

    @Test
    fun `a season costs the sum of its episodes`() = runTest {
        val perEpisode = (1..3).sumOf { estimate.forEpisode(1, it, Rung.ID_240P) }
        assertEquals(perEpisode, estimate.forSeason(1, Rung.ID_240P), 0.001)
    }

    @Test
    fun `the bitrate ceiling derived from MB per hour is self-consistent`() {
        val rung240 = Rung.LADDER.single { it.id == Rung.ID_240P }
        // 155 MB/hr -> about 344 kbps, which is a plausible 240p stream.
        assertEquals(344_444, rung240.maxBitrateBps)
        // 16:9 at the rung's height.
        assertEquals(426, rung240.maxWidth)

        val rung720 = Rung.LADDER.single { it.id == Rung.ID_720P }
        assertEquals(2_333_333, rung720.maxBitrateBps)
        assertEquals(1280, rung720.maxWidth)
    }

    @Test
    fun `the saver cap sits exactly at 360p`() {
        for (rung in Rung.LADDER) {
            assertEquals(
                "${rung.id} above the cap?",
                rung.mbPerHour > Rung.SAVER_CAP_MB_PER_HOUR,
                rung.isAboveSaverCap,
            )
        }
        assertTrue(Rung.LADDER.single { it.id == Rung.ID_360P }.mbPerHour == Rung.SAVER_CAP_MB_PER_HOUR)
    }
}
