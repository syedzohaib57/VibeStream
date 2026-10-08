package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.db.MediaListDao
import com.example.streamingappzb.data.db.MediaListEntity
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.repository.MediaListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * My List over the real catalogue. On this device, in Room, with no account — the one
 * promise the original product made that survives the pivot unchanged.
 */
class MediaListRepositoryImpl(
    private val dao: MediaListDao,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : MediaListRepository {

    override fun observeKeys(): Flow<List<String>> = dao.observeKeys()

    override suspend fun keys(): List<String> = withContext(io) { dao.keys() }

    override suspend fun contains(item: MediaItem): Boolean =
        withContext(io) { dao.contains(item.key) }

    override suspend fun toggle(item: MediaItem): Boolean = withContext(io) {
        val present = dao.contains(item.key)
        if (present) {
            dao.remove(item.key)
        } else {
            dao.add(MediaListEntity(itemKey = item.key, addedAt = clock.nowMillis()))
        }
        !present
    }
}
