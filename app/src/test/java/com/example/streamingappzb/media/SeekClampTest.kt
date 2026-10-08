package com.example.streamingappzb.media

import com.example.streamingappzb.data.player.clampSeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a requested seek lands.
 *
 * Pinned because the original rule clamped against the *catalogue episode's* runtime, and
 * a free source has no episode — so the upper bound was zero, `coerceIn(0, 0)` rewrote
 * every seek to zero, and the scrubber appeared inert on every locally-played and
 * publicly-sourced title. The symptom was reported as "it always starts from the start",
 * which is exactly what clamping to zero does.
 */
class SeekClampTest {

    @Test
    fun `a seek inside the media lands where it was asked to`() {
        assertEquals(45, clampSeek(45, durationSeconds = 600))
    }

    @Test
    fun `a seek past the end stops at the end`() {
        assertEquals(600, clampSeek(900, durationSeconds = 600))
    }

    @Test
    fun `a negative seek lands at the start`() {
        assertEquals(0, clampSeek(-30, durationSeconds = 600))
    }

    /**
     * The regression itself. Before the fix this returned 0 for every input, because the
     * duration of a source with no catalogue episode behind it is 0.
     */
    @Test
    fun `an unknown duration does not collapse the seek to zero`() {
        assertEquals(45, clampSeek(45, durationSeconds = 0))
        assertEquals(8673, clampSeek(8673, durationSeconds = 0))
    }

    /** Still no negatives, even with nothing to clamp against. */
    @Test
    fun `an unknown duration still refuses a negative seek`() {
        assertEquals(0, clampSeek(-5, durationSeconds = 0))
    }

    /** A duration is never negative, but the rule must not invert if one ever is. */
    @Test
    fun `a nonsensical duration is treated as unknown`() {
        assertEquals(45, clampSeek(45, durationSeconds = -1))
    }
}
