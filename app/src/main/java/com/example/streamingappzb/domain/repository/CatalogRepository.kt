package com.example.streamingappzb.domain.repository

import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.model.TitleDetail
import kotlinx.coroutines.flow.Flow

/**
 * The catalogue. Backed by Room, seeded from `assets/catalog.json`, and shaped so the
 * four read-only endpoints in PRD §7 map onto it one for one:
 *
 * - `GET /home?kind=`  -> [heroTitle] + [homeRows]
 * - `GET /titles/{id}` -> [titleDetail]
 * - `GET /search?q=`   -> [search]
 * - `GET /genres`      -> [genres]
 *
 * Reads always resolve from Room, so Home, My List and Downloads render with no network
 * (PRD §8, Offline).
 */
interface CatalogRepository {

    /** Loads the bundled catalogue into Room on first run. Cheap and idempotent after. */
    suspend fun ensureSeeded()

    suspend fun titles(): List<Title>

    fun observeTitles(): Flow<List<Title>>

    suspend fun title(id: Int): Title?

    suspend fun titleDetail(id: Int): TitleDetail?

    /**
     * Every curated row with its titles resolved, in editorial order and *unfiltered* —
     * chip filtering and the eight-row cap belong to
     * [com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase].
     */
    suspend fun homeRows(): List<HomeRow>

    /**
     * The editorial hero for a chip (PRD §6.1: all -> 1, Drama -> 10, Film -> 8,
     * Documentary -> 11). A null [kind] is the "All" chip.
     */
    suspend fun heroTitle(kind: Kind?): Title?

    suspend fun genres(): List<Genre>

    /** The §6.7 ladder, lowest rung first. */
    fun rungs(): List<Rung>

    /** Falls back to the mobile-data default rather than throwing on an unknown id. */
    fun rung(id: String): Rung

    /**
     * The ad plan for a playback session. Pass [adFree] for a downloaded episode: it was
     * monetised at download time and plays with no ads at all (FR-505).
     */
    suspend fun adSchedule(titleId: Int, adFree: Boolean): AdSchedule

    /** Local fuzzy search (FR-104). Used offline, and as the source of truth here. */
    suspend fun search(query: String): List<Title>
}
