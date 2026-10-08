package com.example.streamingappzb.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TitleEntity::class,
        HomeRowEntity::class,
        GenreEntity::class,
        ProgressEntity::class,
        MyListEntity::class,
        DownloadEntity::class,
        MediaEntity::class,
        MediaRowEntity::class,
        MediaListEntity::class,
        TmdbGenreEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MovieHubDatabase : RoomDatabase() {

    abstract fun titleDao(): TitleDao
    abstract fun homeRowDao(): HomeRowDao
    abstract fun genreDao(): GenreDao
    abstract fun progressDao(): ProgressDao
    abstract fun myListDao(): MyListDao
    abstract fun downloadDao(): DownloadDao

    /** The TMDB/AniList cache (v2). */
    abstract fun mediaDao(): MediaDao

    /** My List over real catalogue items, composite-keyed (v3). */
    abstract fun mediaListDao(): MediaListDao

    /** TMDB's genre vocabulary, previously a hardcoded map (v4). */
    abstract fun tmdbGenreDao(): TmdbGenreDao

    companion object {
        private const val NAME = "moviehub.db"

        fun build(context: Context): MovieHubDatabase =
            Room.databaseBuilder(context, MovieHubDatabase::class.java, NAME)
                // The catalogue tables are a cache of a bundled asset and the user tables
                // are cheap to lose, so a destructive migration is acceptable here rather
                // than hand-writing migrations for a v1 schema.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
