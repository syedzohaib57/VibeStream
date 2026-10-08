package com.example.streamingappzb.ui.widget

import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType

/**
 * The colours a poster falls back to when there is no key art.
 *
 * Derived from the item's own id rather than stored or fetched, so it is stable — the same
 * title is always the same colour, across sessions and devices — and costs nothing. The
 * hues come from the app's own palette rather than a random wheel, so a missing poster
 * still looks like part of this product and not like an error state.
 */
data class MediaPalette(val c1: Int, val c2: Int, val motif: Int) {

    companion object {
        /**
         * Deep, saturated starts that read well under the design's dark scrims. Deliberately
         * a small set: a poster row of eight art-less titles should look composed, and 40
         * arbitrary hues would not.
         */
        private val STARTS = intArrayOf(
            0xFF3A2418.toInt(), // ember earth
            0xFF10343A.toInt(), // deep teal
            0xFF1E2145.toInt(), // indigo
            0xFF3A1B2E.toInt(), // plum
            0xFF15331F.toInt(), // forest
            0xFF3A2E12.toInt(), // amber shade
        )

        /** Every start resolves toward near-black, which is what the scrims expect. */
        private const val END = 0xFF0B0B0D.toInt()

        private const val MOTIF_COUNT = 4

        fun of(item: MediaItem): MediaPalette = of(item.id, item.type)

        fun of(id: Int, type: MediaType): MediaPalette {
            // Mixing the type in keeps a movie and a series with the same TMDB id from
            // drawing identically, which would look like a duplicate in a mixed row.
            val seed = (id * 31 + type.ordinal * 7).let { if (it < 0) -it else it }
            return MediaPalette(
                c1 = STARTS[seed % STARTS.size],
                c2 = END,
                motif = (seed / STARTS.size) % MOTIF_COUNT,
            )
        }

        /**
         * A genre tile's colour.
         *
         * Seeded from the *name* rather than the id, so Drama is the same colour on the film
         * tab and the series tab even though TMDB numbers them differently — a browse grid
         * where one genre changes colour per tab reads as a bug.
         */
        fun ofGenre(genre: MediaGenre): MediaPalette {
            val seed = genre.name.fold(0) { acc, c -> acc * 31 + c.code }
                .let { if (it < 0) -it else it }
            return MediaPalette(
                c1 = STARTS[seed % STARTS.size],
                c2 = END,
                motif = (seed / STARTS.size) % MOTIF_COUNT,
            )
        }
    }
}
