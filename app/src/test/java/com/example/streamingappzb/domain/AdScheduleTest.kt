package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.AdCreative
import com.example.streamingappzb.domain.model.AdPlaybackState
import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.AdSlot
import com.example.streamingappzb.domain.model.AdSpot
import com.example.streamingappzb.domain.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance item 8: **skip unlocks at exactly 5 s, and mid-roll ticks line up with the
 * authored breaks.**
 *
 * The state machine is exercised here rather than [com.example.streamingappzb.data.player
 * .AdController] itself, because the controller needs a real ExoPlayer; every timing rule
 * it enforces lives in these value types, which is what makes them testable on the JVM.
 */
class AdScheduleTest {

    private val creative = AdCreative("https://example.test/ad.mp4", StreamType.Progressive)

    private fun spot(
        id: String,
        slot: AdSlot,
        fraction: Float,
        skipAfter: Int = AdSchedule.DEFAULT_SKIP_AFTER_SECONDS,
    ) = AdSpot(
        id = id,
        slot = slot,
        positionFraction = fraction,
        creative = creative,
        skipAfterSeconds = skipAfter,
        maxDurationSeconds = 15,
    )

    private val preRoll = spot("pre", AdSlot.PreRoll, 0f)
    private val schedule = AdSchedule(
        listOf(
            preRoll,
            spot("mid0", AdSlot.MidRoll, 0.33f),
            spot("mid1", AdSlot.MidRoll, 0.66f),
        ),
    )

    @Test
    fun `skip is locked until exactly the fifth second and unlocked from then on`() {
        for (elapsed in 0..4) {
            val state = AdPlaybackState(preRoll, elapsedSeconds = elapsed, durationSeconds = 15)
            assertFalse("skip must be locked at ${elapsed}s", state.canSkip)
        }
        for (elapsed in 5..15) {
            val state = AdPlaybackState(preRoll, elapsedSeconds = elapsed, durationSeconds = 15)
            assertTrue("skip must be unlocked at ${elapsed}s", state.canSkip)
        }
    }

    @Test
    fun `the countdown reads 5 4 3 2 1 and then stops at zero`() {
        val counts = (0..6).map {
            AdPlaybackState(preRoll, elapsedSeconds = it, durationSeconds = 15).secondsUntilSkip
        }
        assertEquals(listOf(5, 4, 3, 2, 1, 0, 0), counts)
    }

    @Test
    fun `an idle state is never in an ad and never skippable`() {
        assertFalse(AdPlaybackState.IDLE.inAd)
        assertFalse(AdPlaybackState.IDLE.canSkip)
        assertEquals(0, AdPlaybackState.IDLE.secondsUntilSkip)
    }

    @Test
    fun `the ticks the rail draws are exactly the authored mid-roll fractions`() {
        assertEquals(listOf(0.33f, 0.66f), schedule.midRollFractions)
        // The pre-roll is not a tick: it plays before the content, not inside it.
        assertEquals(2, schedule.midRolls.size)
        assertEquals(preRoll, schedule.preRoll)
    }

    @Test
    fun `mid-roll fractions are always returned in order`() {
        val scrambled = AdSchedule(
            listOf(
                spot("b", AdSlot.MidRoll, 0.8f),
                spot("a", AdSlot.MidRoll, 0.2f),
                spot("c", AdSlot.MidRoll, 0.5f),
            ),
        )
        assertEquals(listOf(0.2f, 0.5f, 0.8f), scrambled.midRollFractions)
    }

    @Test
    fun `a break's position in seconds follows the runtime it was authored against`() {
        val duration = 2290 // 38:10
        assertEquals(756, spot("m", AdSlot.MidRoll, 0.33f).positionSeconds(duration))
        assertEquals(1511, spot("m", AdSlot.MidRoll, 0.66f).positionSeconds(duration))
        // And they land inside the episode, not past its end.
        assertTrue(schedule.midRolls.all { it.positionSeconds(duration) in 1 until duration })
    }

    @Test
    fun `a downloaded episode has no ads at all`() {
        assertTrue(AdSchedule.NONE.isEmpty)
        assertNull(AdSchedule.NONE.preRoll)
        assertTrue(AdSchedule.NONE.midRollFractions.isEmpty())
    }

    @Test
    fun `the pre-roll is capped at 30 seconds`() {
        assertEquals(30, AdSchedule.MAX_PRE_ROLL_SECONDS)
        assertTrue(schedule.spots.all { it.maxDurationSeconds <= AdSchedule.MAX_PRE_ROLL_SECONDS })
    }

    @Test
    fun `progress and remaining track the elapsed time`() {
        val state = AdPlaybackState(preRoll, elapsedSeconds = 6, durationSeconds = 15)
        assertEquals(9, state.remainingSeconds)
        assertEquals(0.4f, state.progressFraction, 0.001f)
        // A zero duration cannot divide by zero.
        assertEquals(0f, AdPlaybackState(preRoll, 0, 0).progressFraction, 0.001f)
    }

    @Test
    fun `controls are dimmed to the design's opacity while an ad plays`() {
        assertEquals(0.45f, AdPlaybackState.DIMMED_ALPHA, 0.0001f)
    }

    @Test
    fun `resuming past a break does not replay it`() {
        val duration = 2470 // 41:10, so the breaks land at 815s and 1630s

        // Starting from the top owes nothing.
        assertTrue(schedule.midRollsAtOrBefore(0, duration).isEmpty())

        // Resuming at 18:42 is past the 33% break only.
        assertEquals(
            listOf("mid0"),
            schedule.midRollsAtOrBefore(1122, duration).map { it.id },
        )

        // Resuming near the end is past both.
        assertEquals(
            listOf("mid0", "mid1"),
            schedule.midRollsAtOrBefore(2400, duration).map { it.id },
        )

        // Exactly on a break counts as already seen, not as owing one.
        assertEquals(
            listOf("mid0"),
            schedule.midRollsAtOrBefore(815, duration).map { it.id },
        )
        // A second before it does not.
        assertTrue(schedule.midRollsAtOrBefore(814, duration).isEmpty())
    }
}
