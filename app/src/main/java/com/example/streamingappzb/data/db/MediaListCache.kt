package com.example.streamingappzb.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * My List, keyed by `type:id`.
 *
 * A plain integer id is ambiguous against a real catalogue: TMDB numbers films and series
 * in separate spaces, so 1399 is both a film and *Game of Thrones*. Saving one would have
 * silently marked the other as saved too. The composite key is the fix, and it is why this
 * table exists rather than the original integer-keyed one.
 */
@Entity(tableName = "media_list", primaryKeys = ["itemKey"])
data class MediaListEntity(
    val itemKey: String,
    /** Most recently added first is the grid's order, so the timestamp is the sort. */
    val addedAt: Long,
)

@Dao
interface MediaListDao {

    @Query("SELECT itemKey FROM media_list ORDER BY addedAt DESC")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT itemKey FROM media_list ORDER BY addedAt DESC")
    suspend fun keys(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM media_list WHERE itemKey = :itemKey)")
    suspend fun contains(itemKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: MediaListEntity)

    @Query("DELETE FROM media_list WHERE itemKey = :itemKey")
    suspend fun remove(itemKey: String)
}
