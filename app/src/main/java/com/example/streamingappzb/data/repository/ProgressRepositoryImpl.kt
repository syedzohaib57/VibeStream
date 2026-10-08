package com.example.streamingappzb.data.repository

import com.example.streamingappzb.data.catalog.CatalogMapper
import com.example.streamingappzb.data.db.ProgressDao
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProgressRepositoryImpl(
    private val dao: ProgressDao,
    private val io: CoroutineDispatcher,
) : ProgressRepository {

    override fun observeAll(): Flow<List<Progress>> =
        dao.observeAll().map { list -> list.map(CatalogMapper::toDomain) }

    override suspend fun all(): List<Progress> = withContext(io) {
        dao.all().map(CatalogMapper::toDomain)
    }

    override suspend fun get(titleId: Int): Progress? = withContext(io) {
        dao.byTitle(titleId)?.let(CatalogMapper::toDomain)
    }

    override suspend fun put(progress: Progress) = withContext(io) {
        dao.put(CatalogMapper.toEntity(progress))
    }

    override suspend fun delete(titleId: Int) = withContext(io) {
        dao.delete(titleId)
    }
}
