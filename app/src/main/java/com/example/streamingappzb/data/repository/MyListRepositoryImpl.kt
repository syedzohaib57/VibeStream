package com.example.streamingappzb.data.repository

import com.example.streamingappzb.data.db.MyListDao
import com.example.streamingappzb.data.db.MyListEntity
import com.example.streamingappzb.domain.repository.MyListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MyListRepositoryImpl(
    private val dao: MyListDao,
    private val io: CoroutineDispatcher,
) : MyListRepository {

    override fun observe(): Flow<List<Int>> = dao.observeIds()

    override suspend fun ids(): Set<Int> = withContext(io) { dao.ids().toSet() }

    override suspend fun contains(titleId: Int): Boolean = withContext(io) {
        dao.contains(titleId)
    }

    override suspend fun toggle(titleId: Int): Boolean = withContext(io) {
        if (dao.contains(titleId)) {
            dao.remove(titleId)
            false
        } else {
            dao.add(MyListEntity(titleId = titleId, addedAt = System.currentTimeMillis()))
            true
        }
    }
}
