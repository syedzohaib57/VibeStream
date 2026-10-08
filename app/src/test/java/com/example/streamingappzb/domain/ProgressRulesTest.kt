package com.example.streamingappzb.domain

import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.isFinished
import com.example.streamingappzb.domain.model.isResumable
import com.example.streamingappzb.domain.repository.ProgressRepository
import com.example.streamingappzb.domain.usecase.SaveProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §6.3: a position within 30 s of the end counts as finished — a series advances to
 * ep+1 at 0, a film drops out of Continue watching. Under 30 s in is not remembered.
 */
class ProgressRulesTest {

    private class InMemoryProgress : ProgressRepository {
        val stored = mutableMapOf<Int, Progress>()

        override fun observeAll(): Flow<List<Progress>> = flowOf(stored.values.toList())

        override suspend fun all(): List<Progress> = stored.values.toList()

        override suspend fun get(titleId: Int): Progress? = stored[titleId]

        override suspend fun put(progress: Progress) {
            stored[progress.titleId] = progress
        }

        override suspend fun delete(titleId: Int) {
            stored.remove(titleId)
        }
    }

    private val duration = 2290 // 38:10, the design's first-episode length

    @Test
    fun `the finished window is the last 30 seconds`() {
        assertFalse(isFinished(duration - 31, duration))
        assertTrue(isFinished(duration - 30, duration))
        assertTrue(isFinished(duration, duration))
    }

    @Test
    fun `resumable means past 30 seconds and not yet finished`() {
        assertFalse(isResumable(30, duration))
        assertTrue(isResumable(31, duration))
        assertTrue(isResumable(duration - 31, duration))
        assertFalse(isResumable(duration - 30, duration))
    }

    @Test
    fun `a mid-episode position is saved`() = runTest {
        val repo = InMemoryProgress()
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())

        val outcome = useCase(
            titleId = 1,
            episode = 1,
            positionSeconds = 1122,
            durationSeconds = duration,
            nowMillis = 1_000L,
        )
        assertTrue(outcome is SaveProgressUseCase.Outcome.Saved)
        assertEquals(1122, repo.stored[1]?.positionSeconds)
        assertEquals(1, repo.stored[1]?.episode)
    }

    @Test
    fun `finishing an episode advances Continue watching to the next one at zero`() = runTest {
        val repo = InMemoryProgress()
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())

        val outcome = useCase(
            titleId = 1,
            episode = 1,
            positionSeconds = duration - 5,
            durationSeconds = duration,
            nowMillis = 1_000L,
        )
        assertEquals(SaveProgressUseCase.Outcome.AdvancedTo(2), outcome)
        assertEquals(2, repo.stored[1]?.episode)
        // Position 0 deliberately bypasses the "under 30 s" rule: this is a hand-off,
        // not a live position.
        assertEquals(0, repo.stored[1]?.positionSeconds)
    }

    @Test
    fun `finishing the last episode drops the title from Continue watching`() = runTest {
        val repo = InMemoryProgress()
        // The fake's series has 3 episodes.
        repo.stored[1] = Progress(1, 3, 100, 0L)
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())

        val outcome = useCase(
            titleId = 1,
            episode = 3,
            positionSeconds = duration,
            durationSeconds = duration,
            nowMillis = 1_000L,
        )
        assertEquals(SaveProgressUseCase.Outcome.Completed, outcome)
        assertNull(repo.stored[1])
    }

    @Test
    fun `finishing a film drops it from Continue watching`() = runTest {
        val repo = InMemoryProgress()
        repo.stored[4] = Progress(4, 1, 3240, 0L)
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())

        val filmDuration = 124 * 60
        val outcome = useCase(
            titleId = 4,
            episode = 1,
            positionSeconds = filmDuration - 10,
            durationSeconds = filmDuration,
            nowMillis = 1_000L,
        )
        assertEquals(SaveProgressUseCase.Outcome.Completed, outcome)
        assertNull(repo.stored[4])
    }

    @Test
    fun `restarting from the top takes the title off the row`() = runTest {
        val repo = InMemoryProgress()
        repo.stored[1] = Progress(1, 4, 1122, 0L)
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())

        val outcome = useCase(
            titleId = 1,
            episode = 1,
            positionSeconds = 8,
            durationSeconds = duration,
            nowMillis = 1_000L,
        )
        assertEquals(SaveProgressUseCase.Outcome.Discarded, outcome)
        assertNull(repo.stored[1])
    }

    @Test
    fun `a zero duration is ignored rather than dividing by it`() = runTest {
        val repo = InMemoryProgress()
        val useCase = SaveProgressUseCase(repo, FakeCatalogRepository())
        assertEquals(
            SaveProgressUseCase.Outcome.Discarded,
            useCase(1, 1, 100, 0, 1_000L),
        )
    }
}
