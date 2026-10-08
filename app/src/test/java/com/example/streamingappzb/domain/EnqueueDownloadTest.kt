package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.usecase.EnqueueDownloadUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-301..303 and acceptance item 11.
 *
 * The stream-only guarantee is tested here rather than on device because the sample
 * catalogue's only `dl = false` title is a **film** — which has no episode list, so the
 * lock glyph on an episode row is unreachable through the UI. The rule itself lives in
 * this use case and applies to a stream-only series just the same.
 */
class EnqueueDownloadTest {

    private fun useCase(
        downloads: FakeDownloadRepository,
        network: NetworkState,
        wifiOnly: Boolean = true,
    ) = EnqueueDownloadUseCase(
        catalog = FakeCatalogRepository(),
        downloads = downloads,
        settings = FakeSettingsRepository(wifiOnly = wifiOnly),
        network = FakeNetworkRepository(network),
    )

    @Test
    fun `a stream-only title is refused and nothing is enqueued`() = runTest {
        val downloads = FakeDownloadRepository()
        // Id 9 is a stream-only *series*, so this is the case the UI cannot reach.
        val result = useCase(downloads, NetworkState.Wifi)(
            titleId = FakeCatalogRepository.STREAM_ONLY.id,
            episodes = listOf(1, 2, 3),
            rungId = Rung.DEFAULT_DOWNLOAD,
        )
        assertEquals(EnqueueDownloadUseCase.Result.StreamOnly, result)
        assertTrue("a stream-only title must never enqueue", downloads.enqueued.isEmpty())
    }

    @Test
    fun `on mobile data with Wi-Fi only on, the item is parked rather than started`() = runTest {
        val downloads = FakeDownloadRepository()
        val result = useCase(downloads, NetworkState.Cellular, wifiOnly = true)(
            titleId = FakeCatalogRepository.SERIES.id,
            episodes = listOf(1),
            rungId = Rung.DEFAULT_DOWNLOAD,
        )
        assertEquals(
            EnqueueDownloadUseCase.Result.Enqueued(1, 1, waitingForWifi = true),
            result,
        )
        val request = downloads.enqueued.single()
        assertTrue("must be parked for Wi-Fi", request.queuedForWifi)
        assertFalse("parking is not consent to spend data", request.allowMetered)
    }

    @Test
    fun `Use mobile data now starts it and records the consent`() = runTest {
        val downloads = FakeDownloadRepository()
        val result = useCase(downloads, NetworkState.Cellular, wifiOnly = true)(
            titleId = FakeCatalogRepository.SERIES.id,
            episodes = listOf(1),
            rungId = Rung.DEFAULT_DOWNLOAD,
            useMobileDataNow = true,
        )
        assertEquals(
            EnqueueDownloadUseCase.Result.Enqueued(1, 1, waitingForWifi = false),
            result,
        )
        val request = downloads.enqueued.single()
        assertFalse(request.queuedForWifi)
        assertTrue("explicit consent must be recorded", request.allowMetered)
    }

    @Test
    fun `on Wi-Fi nothing is parked and no metered consent is implied`() = runTest {
        val downloads = FakeDownloadRepository()
        useCase(downloads, NetworkState.Wifi, wifiOnly = true)(
            titleId = FakeCatalogRepository.SERIES.id,
            episodes = listOf(1),
            rungId = Rung.DEFAULT_DOWNLOAD,
            useMobileDataNow = true,
        )
        val request = downloads.enqueued.single()
        assertFalse(request.queuedForWifi)
        // Enqueuing on Wi-Fi must not quietly license spending data later.
        assertFalse(request.allowMetered)
    }

    @Test
    fun `the MB carried into the queue is the figure the sheet showed`() = runTest {
        val downloads = FakeDownloadRepository()
        useCase(downloads, NetworkState.Wifi)(
            titleId = FakeCatalogRepository.SERIES.id,
            episodes = listOf(1),
            rungId = Rung.ID_240P,
        )
        // Episode one is 38:10 = 2290s; 155 MB/hr * 2290 / 3600 = 98.6 MB.
        assertEquals(98.6, downloads.enqueued.single().megabytes, 0.1)
    }

    @Test
    fun `asking again for something already queued does not duplicate or restart it`() = runTest {
        val downloads = FakeDownloadRepository(
            listOf(
                DownloadItem(
                    titleId = 1,
                    episode = 1,
                    state = DlState.Downloading,
                    percent = 40,
                    rungId = Rung.ID_240P,
                    megabytes = 98.6,
                    expiresAtMillis = null,
                ),
            ),
        )
        val result = useCase(downloads, NetworkState.Wifi)(
            titleId = 1,
            episodes = listOf(1),
            rungId = Rung.DEFAULT_DOWNLOAD,
        )
        assertEquals(EnqueueDownloadUseCase.Result.AlreadyPresent, result)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `a season enqueues every episode once`() = runTest {
        val downloads = FakeDownloadRepository()
        val result = useCase(downloads, NetworkState.Wifi)(
            titleId = FakeCatalogRepository.SERIES.id,
            episodes = listOf(1, 2, 3),
            rungId = Rung.DEFAULT_DOWNLOAD,
        )
        assertEquals(EnqueueDownloadUseCase.Result.Enqueued(3, 1, false), result)
        assertEquals(listOf(1, 2, 3), downloads.enqueued.map { it.episode })
    }
}
