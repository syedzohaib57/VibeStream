package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.db.MediaEntity
import com.example.streamingappzb.data.db.MediaRowEntity
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType

/** Between the cache tables and the domain model. */
object MediaMapping {

    private const val SEPARATOR = "|"

    fun toEntity(item: MediaItem, now: Long) = MediaEntity(
        itemKey = item.key,
        id = item.id,
        type = item.type.name,
        title = item.title,
        overview = item.overview,
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        year = item.year,
        rating = item.rating,
        genres = item.genres.joinToString(SEPARATOR),
        releaseDate = item.releaseDate,
        cachedAt = now,
    )

    fun toItem(entity: MediaEntity): MediaItem? {
        val type = runCatching { MediaType.valueOf(entity.type) }.getOrNull() ?: return null
        return MediaItem(
            id = entity.id,
            type = type,
            title = entity.title,
            overview = entity.overview,
            posterPath = entity.posterPath,
            backdropPath = entity.backdropPath,
            year = entity.year,
            rating = entity.rating,
            genres = entity.genres.split(SEPARATOR).filter { it.isNotBlank() },
            releaseDate = entity.releaseDate,
        )
    }

    fun toItems(entities: List<MediaEntity>): List<MediaItem> = entities.mapNotNull(::toItem)

    fun members(
        rowKey: String,
        rowTitle: String,
        rowOrder: Int,
        items: List<MediaItem>,
        now: Long,
    ): List<MediaRowEntity> = items.mapIndexed { index, item ->
        MediaRowEntity(
            rowKey = rowKey,
            itemKey = item.key,
            position = index,
            rowTitle = rowTitle,
            rowOrder = rowOrder,
            cachedAt = now,
        )
    }

    /** `Movie:popular_movies`; the tab prefix is what a tab query filters on. */
    fun rowKey(tab: MediaType?, rowKey: String): String = "${tabPrefix(tab)}$rowKey"

    fun tabPrefix(tab: MediaType?): String = "${tab?.name ?: "All"}:"
}
