package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.ContinueItem
import com.example.streamingappzb.domain.model.HomeFeed
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.ProgressRepository

/**
 * Builds Home for a chip (PRD §6.1).
 *
 * Two rules are enforced here rather than in the fragment, because they are acceptance
 * items and belong somewhere unit-testable:
 *
 * - **A row emptied by filtering is dropped, never rendered empty** (§6.1).
 * - **Home shows at most eight rows *including* Continue watching** (§8.5, item 3).
 *
 * Choosing a chip also swaps the hero, from the editorial map in the catalogue.
 */
class GetHomeFeedUseCase(
    private val catalog: CatalogRepository,
    private val progress: ProgressRepository,
) {

    /** @param kind the selected chip; null is "All". */
    suspend operator fun invoke(kind: Kind?): HomeFeed {
        val hero = catalog.heroTitle(kind)
        val saved = progress.all()
        val byId = catalog.titles().associateBy { it.id }

        val continueWatching = saved.mapNotNull { p -> toContinueItem(p, byId, kind) }

        // Continue watching occupies one of the eight slots when it is shown at all.
        val rowBudget = HomeFeed.MAX_ROWS - if (continueWatching.isEmpty()) 0 else 1

        val rows = catalog.homeRows()
            .map { row -> row.copy(titles = row.titles.filter { keep(it.kind, kind) }) }
            .filter { it.titles.isNotEmpty() }
            .take(rowBudget)

        return HomeFeed(
            hero = hero,
            heroProgress = hero?.let { h -> saved.firstOrNull { it.titleId == h.id } },
            continueWatching = continueWatching,
            rows = rows,
        )
    }

    private suspend fun toContinueItem(
        p: Progress,
        byId: Map<Int, com.example.streamingappzb.domain.model.Title>,
        kind: Kind?,
    ): ContinueItem? {
        val title = byId[p.titleId] ?: return null
        if (!keep(title.kind, kind)) return null
        // Episode lists are generated, so this is a cheap lookup rather than a query.
        val episode = catalog.titleDetail(title.id)?.episode(p.episode) ?: return null
        return ContinueItem(progress = p, title = title, episode = episode)
    }

    private fun keep(titleKind: Kind, chip: Kind?): Boolean = chip == null || titleKind == chip
}

/** Resolves a row's heading: a shipped row prefers its localised string (PRD §8). */
fun HomeRow.headingOrFallback(resolve: (String) -> String?): String =
    resolve(key) ?: fallbackTitle
