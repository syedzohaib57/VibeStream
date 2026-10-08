package com.example.streamingappzb.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * TMDB's genre vocabulary, cached.
 *
 * Every row endpoint returns `genre_ids` and no names, and the names come from a separate
 * endpoint. Those used to be a hardcoded map in the app, which worked but meant genre names
 * never localised and a new TMDB genre would read as blank. They are now fetched once and
 * kept here; the hardcoded map survives as the cold-start fallback so the first frame after
 * install still has names — see [com.example.streamingappzb.data.remote.tmdb.TmdbGenres].
 *
 * Keyed by `scope:id`, not `id`: TMDB's film and television genre ids are separate spaces
 * that overlap. 16 is Animation in both, but 10765 is *Sci-Fi & Fantasy* and exists only
 * for television, and nothing guarantees a future collision stays benign.
 */
@Entity(tableName = "tmdb_genres")
data class TmdbGenreEntity(
    @androidx.room.PrimaryKey val key: String,
    val genreId: Int,
    val name: String,
    /** "Movie" or "Tv", matching `GenreScope`. */
    val scope: String,
    val cachedAt: Long,
)

@Dao
interface TmdbGenreDao {

    @Query("SELECT * FROM tmdb_genres ORDER BY name ASC")
    suspend fun all(): List<TmdbGenreEntity>

    @Query("SELECT * FROM tmdb_genres WHERE scope = :scope ORDER BY name ASC")
    suspend fun forScope(scope: String): List<TmdbGenreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(genres: List<TmdbGenreEntity>)

    @Query("SELECT MIN(cachedAt) FROM tmdb_genres")
    suspend fun oldestTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM tmdb_genres")
    suspend fun count(): Int
}
