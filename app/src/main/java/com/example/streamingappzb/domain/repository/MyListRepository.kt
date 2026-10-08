package com.example.streamingappzb.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * My List — in Room, on this phone, with no account (PRD §6.6). Toggled from the hero,
 * Title detail, and a long press on any poster.
 */
interface MyListRepository {

    /** Most recently added first, which is the grid's order. */
    fun observe(): Flow<List<Int>>

    suspend fun ids(): Set<Int>

    suspend fun contains(titleId: Int): Boolean

    /** Returns the state after toggling, so callers can pick the right toast. */
    suspend fun toggle(titleId: Int): Boolean
}
