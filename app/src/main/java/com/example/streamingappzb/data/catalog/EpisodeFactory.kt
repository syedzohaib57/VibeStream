package com.example.streamingappzb.data.catalog

import com.example.streamingappzb.domain.model.Episode
import com.example.streamingappzb.domain.model.Title

/**
 * Generates a title's episode list.
 *
 * This is Catalog.jsx's `episodesOf` exactly:
 *
 * ```js
 * function episodesOf(t) {
 *   if (t.kind === 'Film') return [{ n: 1, name: t.title, dur: t.mins * 60 }];
 *   return Array.from({ length: t.eps }, (_, i) => ({
 *     n: i + 1,
 *     name: EP_NAMES[i % EP_NAMES.length],
 *     dur: (38 + ((i * 7) % 9)) * 60 + 10,
 *   }));
 * }
 * ```
 *
 * Episodes are derived rather than stored: there are roughly 200 of them across the
 * sample catalogue and every field is a pure function of the title, so a table would just
 * be a second copy of this formula that could fall out of step with the design.
 *
 * A Film is one episode named after the title, which is what lets the player and the
 * progress store treat films and series identically.
 */
class EpisodeFactory(private val episodeNames: List<String>) {

    fun episodesFor(title: Title): List<Episode> {
        if (title.isFilm) {
            return listOf(
                Episode(
                    titleId = title.id,
                    number = 1,
                    name = title.title,
                    durationSeconds = (title.runtimeMinutes ?: 0) * 60,
                ),
            )
        }
        val count = title.episodeCount ?: 0
        if (count <= 0 || episodeNames.isEmpty()) return emptyList()
        return List(count) { i ->
            Episode(
                titleId = title.id,
                number = i + 1,
                name = episodeNames[i % episodeNames.size],
                durationSeconds = durationSecondsFor(i),
            )
        }
    }

    companion object {
        /**
         * The design's own formula: 38 to 46 minutes, plus 10 seconds, varying per index
         * so the episode list does not look mechanically uniform.
         */
        fun durationSecondsFor(zeroBasedIndex: Int): Int =
            (38 + ((zeroBasedIndex * 7) % 9)) * 60 + 10
    }
}
