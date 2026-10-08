package com.example.streamingappzb.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The offline cache for real catalogue data.
 *
 * TMDB and AniList both forbid *redistributing* their data, but caching it on the device
 * for the app's own use is expected and necessary — it is what lets Home open instantly
 * and still draw with no network. Nothing here is exported or synced anywhere.
 *
 * Two tables rather than one blob: [MediaEntity] holds each title once, and
 * [MediaRowEntity] holds the ordered membership of each row. A title appearing in three
 * rows is stored once, and a row refresh rewrites only its own membership.
 */
@Entity(tableName = "media", primaryKeys = ["itemKey"])
data class MediaEntity(
    val itemKey: String,
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val rating: Double?,
    /** Joined with `|`, which cannot appear in a TMDB or AniList genre name. */
    val genres: String,
    val releaseDate: String?,
    val cachedAt: Long,
)

@Entity(
    tableName = "media_row",
    primaryKeys = ["rowKey", "itemKey"],
    indices = [Index("rowKey"), Index("itemKey")],
)
data class MediaRowEntity(
    /** e.g. `Anime:anime_season` — the tab and the row, so tabs cannot collide. */
    val rowKey: String,
    val itemKey: String,
    val position: Int,
    val rowTitle: String,
    val rowOrder: Int,
    val cachedAt: Long,
)

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRowMembers(members: List<MediaRowEntity>)

    @Query("DELETE FROM media_row WHERE rowKey = :rowKey")
    suspend fun clearRow(rowKey: String)

    /** Replaces one row's membership atomically, so it is never half-written. */
    @Transaction
    suspend fun replaceRow(rowKey: String, items: List<MediaEntity>, members: List<MediaRowEntity>) {
        upsertItems(items)
        clearRow(rowKey)
        upsertRowMembers(members)
    }

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_row r ON r.itemKey = m.itemKey
        WHERE r.rowKey LIKE :tabPrefix || '%'
        ORDER BY r.rowOrder, r.position
        """,
    )
    suspend fun itemsForTab(tabPrefix: String): List<MediaEntity>

    @Query(
        """
        SELECT DISTINCT r.rowKey, r.rowTitle, r.rowOrder, r.cachedAt FROM media_row r
        WHERE r.rowKey LIKE :tabPrefix || '%'
        ORDER BY r.rowOrder
        """,
    )
    suspend fun rowsForTab(tabPrefix: String): List<CachedRow>

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_row r ON r.itemKey = m.itemKey
        WHERE r.rowKey = :rowKey ORDER BY r.position
        """,
    )
    suspend fun itemsForRow(rowKey: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE itemKey = :itemKey")
    suspend fun item(itemKey: String): MediaEntity?

    @Query("SELECT * FROM media WHERE itemKey IN (:keys)")
    suspend fun items(keys: List<String>): List<MediaEntity>

    /** The offline fallback for search: substring over what has already been seen. */
    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' ORDER BY rating DESC LIMIT 40")
    suspend fun searchCached(query: String): List<MediaEntity>

    @Query("SELECT MIN(cachedAt) FROM media_row WHERE rowKey LIKE :tabPrefix || '%'")
    suspend fun oldestRowTimestamp(tabPrefix: String): Long?

    @Query("SELECT COUNT(*) FROM media_row WHERE rowKey LIKE :tabPrefix || '%'")
    fun observeTabSize(tabPrefix: String): Flow<Int>

    /** Housekeeping: drop cached titles no row references any more. */
    @Query("DELETE FROM media WHERE itemKey NOT IN (SELECT itemKey FROM media_row)")
    suspend fun pruneOrphans(): Int
}

/** One row's identity, without its members. */
data class CachedRow(
    val rowKey: String,
    val rowTitle: String,
    val rowOrder: Int,
    val cachedAt: Long,
)
