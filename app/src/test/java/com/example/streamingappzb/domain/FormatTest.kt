package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.format.Format
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Parity with `fmt` and `fmtMB` in the design's Shared.jsx — the player's time codes and
 * every MB figure have to read exactly as designed.
 */
class FormatTest {

    @Before
    fun fixLocale() {
        // Format uses the default locale for decimal separators; pin it so the assertions
        // are about the format, not the machine.
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `time matches the design's fmt`() {
        assertEquals("0:00", Format.time(0))
        assertEquals("0:07", Format.time(7))
        assertEquals("1:05", Format.time(65))
        // Minutes are unpadded under an hour: "9:05", not "09:05".
        assertEquals("9:05", Format.time(545))
        assertEquals("38:10", Format.time(2290))
        assertEquals("1:01:01", Format.time(3661))
        // Blue Tide is 124 minutes, which is 2h 4m.
        assertEquals("2:04:00", Format.time(124 * 60))
        assertEquals("1:52:00", Format.time(112 * 60))
    }

    @Test
    fun `time never goes negative`() {
        assertEquals("0:00", Format.time(-5))
    }

    @Test
    fun `megabytes matches the design's fmtMB`() {
        assertEquals("80 MB", Format.megabytes(80))
        assertEquals("155 MB", Format.megabytes(155))
        assertEquals("540 MB", Format.megabytes(540))
        // The switch to GB happens at 1000, with two decimals.
        assertEquals("1.05 GB", Format.megabytes(1050))
        assertEquals("1.85 GB", Format.megabytes(1850))
        assertEquals("999 MB", Format.megabytes(999.4))
    }

    @Test
    fun `runtime parts feed the hero's 1h 52m`() {
        assertEquals(1 to 52, Format.runtimeParts(112))
        assertEquals(2 to 4, Format.runtimeParts(124))
        assertEquals(0 to 45, Format.runtimeParts(45))
    }

    @Test
    fun `minutes round up so nothing in progress reads as zero`() {
        assertEquals(1, Format.minutesFromSeconds(1))
        assertEquals(1, Format.minutesFromSeconds(60))
        assertEquals(2, Format.minutesFromSeconds(61))
        assertEquals(39, Format.minutesFromSeconds(2290))
    }

    @Test
    fun `percent is clamped`() {
        assertEquals(0, Format.percent(-1f))
        assertEquals(50, Format.percent(0.5f))
        assertEquals(100, Format.percent(1.5f))
    }
}
