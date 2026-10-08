package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.GenreScope

/**
 * TMDB's genre vocabulary.
 *
 * Separate from [MediaRepository] because its lifetime is completely different: the
 * catalogue changes hourly and is cached for six hours, while the genre list changes about
 * once a year and is cached for a month. Folding them together would mean either
 * re-fetching genres with every feed refresh or never refreshing them at all.
 *
 * [name] is deliberately synchronous. It is called once per poster while a row is being
 * bound, so it reads an in-memory map — [ensureLoaded] is what fills it.
 */
interface GenreRepository {

    /** Loads the cache, refreshing behind it if stale. Safe to call from anywhere, often. */
    suspend fun ensureLoaded()

    /** Every genre in one space, alphabetical — the browse grid's contents. */
    suspend fun genres(scope: GenreScope): List<MediaGenre>

    /** A single name, or null for an id neither TMDB nor the built-in list knows. */
    fun name(id: Int): String?

    /** At most [limit] names, for a metadata line that must not wrap. */
    fun names(ids: List<Int>, limit: Int = 3): List<String>
}
