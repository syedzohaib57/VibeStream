package com.example.streamingappzb.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TitleDao {

    @Query("SELECT * FROM titles ORDER BY sortOrder ASC")
    suspend fun all(): List<TitleEntity>

    @Query("SELECT * FROM titles ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun byId(id: Int): TitleEntity?

    @Query("SELECT COUNT(*) FROM titles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(titles: List<TitleEntity>)

    @Query("DELETE FROM titles")
    suspend fun clear()
}

@Dao
interface HomeRowDao {

    @Query("SELECT * FROM home_rows ORDER BY sortOrder ASC")
    suspend fun all(): List<HomeRowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<HomeRowEntity>)

    @Query("DELETE FROM home_rows")
    suspend fun clear()
}

@Dao
interface GenreDao {

    @Query("SELECT * FROM genres ORDER BY sortOrder ASC")
    suspend fun all(): List<GenreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(genres: List<GenreEntity>)

    @Query("DELETE FROM genres")
    suspend fun clear()
}

@Dao
interface ProgressDao {

    /** Most recently watched first — the order Continue watching renders in. */
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun all(): List<ProgressEntity>

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE titleId = :titleId")
    suspend fun byTitle(titleId: Int): ProgressEntity?

    @Upsert
    suspend fun put(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE titleId = :titleId")
    suspend fun delete(titleId: Int)
}

@Dao
interface MyListDao {

    @Query("SELECT titleId FROM my_list ORDER BY addedAt DESC")
    suspend fun ids(): List<Int>

    @Query("SELECT titleId FROM my_list ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM my_list WHERE titleId = :titleId)")
    suspend fun contains(titleId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: MyListEntity)

    @Query("DELETE FROM my_list WHERE titleId = :titleId")
    suspend fun remove(titleId: Int)
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt ASC")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE key = :key")
    suspend fun byKey(key: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE titleId = :titleId AND episode = :episode")
    suspend fun byEpisode(titleId: Int, episode: Int): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state = :state ORDER BY createdAt ASC")
    suspend fun byState(state: String): List<DownloadEntity>

    @Upsert
    suspend fun put(download: DownloadEntity)

    @Upsert
    suspend fun putAll(downloads: List<DownloadEntity>)

    @Query("UPDATE downloads SET state = :state, percent = :percent WHERE key = :key")
    suspend fun updateState(key: String, state: String, percent: Int)

    @Query("UPDATE downloads SET state = :state, percent = 100, expiresAtMillis = :expiresAt WHERE key = :key")
    suspend fun markDone(key: String, state: String, expiresAt: Long?)

    @Query("UPDATE downloads SET offlineLicenseKeySetId = :keySetId WHERE key = :key")
    suspend fun setKeySetId(key: String, keySetId: ByteArray?)

    /**
     * How many unfinished items the viewer granted mobile-data consent to. While this is
     * zero and Wi-Fi-only is on, the engine requirement stays NETWORK_UNMETERED.
     */
    @Query("SELECT COUNT(*) FROM downloads WHERE allowMetered = 1 AND state != :done")
    suspend fun pendingMeteredConsent(done: String): Int

    @Query("SELECT * FROM downloads WHERE state != :done ORDER BY createdAt ASC")
    suspend fun pending(done: String): List<DownloadEntity>

    /**
     * Wi-Fi arrived: everything parked by "Queue for Wi-Fi" becomes queued
     * (FR-303 / index.html's network effect).
     */
    @Query("UPDATE downloads SET state = :queued, queuedForWifi = 0 WHERE state = :waiting")
    suspend fun promoteWaiting(waiting: String, queued: String)

    @Query("DELETE FROM downloads WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM downloads WHERE expiresAtMillis IS NOT NULL AND expiresAtMillis <= :nowMillis")
    suspend fun expired(nowMillis: Long): List<DownloadEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE titleId = :titleId AND episode = :episode AND state = :done)")
    suspend fun isDone(titleId: Int, episode: Int, done: String): Boolean
}
