package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.Progress
import kotlinx.coroutines.flow.Flow

/**
 * Continue watching, in Room so it survives process death (acceptance item 12).
 *
 * Deliberately dumb: the "within 30 s of the end counts as finished" rule lives in
 * [com.example.streamingappzb.domain.usecase.SaveProgressUseCase] so it is unit-testable
 * without a database.
 */
interface ProgressRepository {

    /** Most recently watched first — the order Continue watching renders in. */
    fun observeAll(): Flow<List<Progress>>

    suspend fun all(): List<Progress>

    suspend fun get(titleId: Int): Progress?

    suspend fun put(progress: Progress)

    suspend fun delete(titleId: Int)
}
