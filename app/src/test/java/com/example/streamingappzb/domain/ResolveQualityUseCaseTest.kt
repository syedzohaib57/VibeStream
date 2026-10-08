package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-204, every branch:
 *
 * - Wi-Fi -> 720p
 * - mobile data, Saver on -> 240p, and every rung above 300 MB/hr is locked
 * - mobile data, Saver off -> 480p
 * - a manual pick holds until one of those changes
 */
class ResolveQualityUseCaseTest {

    private val useCase = ResolveQualityUseCase(FakeCatalogRepository())

    @Test
    fun `wifi defaults to 720p`() {
        assertEquals(
            Rung.ID_720P,
            useCase.defaultFor(NetworkState.Wifi, saver = true).id,
        )
        assertEquals(
            Rung.ID_720P,
            useCase.defaultFor(NetworkState.Wifi, saver = false).id,
        )
    }

    @Test
    fun `mobile data with saver on defaults to 240p`() {
        assertEquals(
            Rung.ID_240P,
            useCase.defaultFor(NetworkState.Cellular, saver = true).id,
        )
    }

    @Test
    fun `mobile data with saver off defaults to 480p`() {
        assertEquals(
            Rung.ID_480P,
            useCase.defaultFor(NetworkState.Cellular, saver = false).id,
        )
    }

    @Test
    fun `saver caps mobile data at 360p and locks everything above it`() {
        val locked = Rung.LADDER.filter {
            useCase.isLocked(it, NetworkState.Cellular, saver = true)
        }
        assertEquals(
            "only rungs above 300 MB/hr are locked",
            listOf(Rung.ID_480P, Rung.ID_720P, Rung.ID_1080P),
            locked.map { it.id },
        )
        // 360p is exactly the cap, so it stays available.
        assertFalse(useCase.isLocked(useCase.rungOf(Rung.ID_360P), NetworkState.Cellular, true))
    }

    @Test
    fun `nothing is locked on wifi, whatever the saver setting`() {
        for (saver in listOf(true, false)) {
            assertTrue(
                Rung.LADDER.none { useCase.isLocked(it, NetworkState.Wifi, saver) },
            )
        }
    }

    @Test
    fun `nothing is locked on mobile data once saver is off`() {
        assertTrue(
            Rung.LADDER.none { useCase.isLocked(it, NetworkState.Cellular, saver = false) },
        )
    }

    @Test
    fun `a manual pick is honoured while it is permitted`() {
        assertEquals(
            Rung.ID_1080P,
            useCase.resolve(NetworkState.Wifi, saver = true, manualRungId = Rung.ID_1080P).id,
        )
        assertEquals(
            Rung.ID_144P,
            useCase.resolve(NetworkState.Cellular, saver = true, manualRungId = Rung.ID_144P).id,
        )
    }

    @Test
    fun `a manual pick that has become locked falls back to the default`() {
        // Picked on Wi-Fi, then the phone moves onto metered data with Saver on: 1080p
        // must not leak through.
        assertEquals(
            Rung.ID_240P,
            useCase.resolve(NetworkState.Cellular, saver = true, manualRungId = Rung.ID_1080P).id,
        )
    }

    @Test
    fun `no manual pick resolves to the default`() {
        assertEquals(
            Rung.ID_480P,
            useCase.resolve(NetworkState.Cellular, saver = false, manualRungId = null).id,
        )
    }

    @Test
    fun `the sheet marks exactly one row selected and locks the right ones`() {
        val options = useCase.options(NetworkState.Cellular, saver = true, currentRungId = Rung.ID_240P)
        assertEquals(Rung.LADDER.size, options.size)
        assertEquals(1, options.count { it.selected })
        assertEquals(Rung.ID_240P, options.single { it.selected }.rung.id)
        assertEquals(3, options.count { it.locked })
    }

    @Test
    fun `offline shows the saver default rather than a wifi rung`() {
        assertEquals(
            Rung.ID_240P,
            useCase.defaultFor(NetworkState.Offline, saver = true).id,
        )
    }

    private fun ResolveQualityUseCase.rungOf(id: String): Rung =
        Rung.LADDER.single { it.id == id }
}
