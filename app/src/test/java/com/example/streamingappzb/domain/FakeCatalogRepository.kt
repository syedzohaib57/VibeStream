package com.example.streamingappzb.domain

import com.example.streamingappzb.data.catalog.EpisodeFactory
import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.Stream
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.model.TitleDetail
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.search.FuzzyMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A JVM-only catalogue, so the use cases can be tested without Room or an asset. */
class FakeCatalogRepository(
    private val titles: List<Title> = listOf(SERIES, FILM, STREAM_ONLY),
    private val rows: List<HomeRow> = emptyList(),
    private val heroByKind: Map<String, Int> = mapOf("all" to 1),
    private val episodeNames: List<String> = listOf("First Step", "The Stranger", "Letters"),
) : CatalogRepository {

    private val factory = EpisodeFactory(episodeNames)

    var seeded = false
        private set

    override suspend fun ensureSeeded() {
        seeded = true
    }

    override suspend fun titles(): List<Title> = titles

    override fun observeTitles(): Flow<List<Title>> = flowOf(titles)

    override suspend fun title(id: Int): Title? = titles.firstOrNull { it.id == id }

    override suspend fun titleDetail(id: Int): TitleDetail? =
        title(id)?.let { TitleDetail(it, factory.episodesFor(it)) }

    override suspend fun homeRows(): List<HomeRow> = rows

    override suspend fun heroTitle(kind: Kind?): Title? =
        heroByKind[kind?.name ?: "all"]?.let { id -> title(id) }

    override suspend fun genres(): List<Genre> = emptyList()

    override fun rungs(): List<Rung> = Rung.LADDER

    override fun rung(id: String): Rung =
        Rung.LADDER.firstOrNull { it.id == id } ?: Rung.LADDER[1]

    override suspend fun adSchedule(titleId: Int, adFree: Boolean): AdSchedule = AdSchedule.NONE

    override suspend fun search(query: String): List<Title> = FuzzyMatcher.filter(titles, query)

    companion object {
        fun title(
            id: Int,
            name: String,
            kind: Kind = Kind.Drama,
            episodes: Int? = 3,
            minutes: Int? = null,
            downloadable: Boolean = true,
            fresh: Boolean = false,
            adBreaks: List<Float> = listOf(0.33f, 0.66f),
        ) = Title(
            id = id,
            title = name,
            kind = kind,
            year = 2022,
            episodeCount = episodes,
            runtimeMinutes = minutes,
            genre = "Drama",
            synopsis = "",
            cast = emptyList(),
            c1 = 0,
            c2 = 0,
            motif = 0,
            artUrl = null,
            downloadable = downloadable,
            fresh = fresh,
            adBreaks = adBreaks,
            stream = Stream("https://example.test/a.m3u8", StreamType.Hls),
        )

        val SERIES = title(1, "Moonlit Night")

        val FILM = title(
            id = 4,
            name = "Blue Tide",
            kind = Kind.Film,
            episodes = null,
            minutes = 124,
            adBreaks = listOf(0.28f, 0.55f, 0.8f),
        )

        /** `dl = false`: the rights record forbids downloading (PRD §6.2). */
        val STREAM_ONLY = title(9, "Cold Wind", downloadable = false)
    }
}
