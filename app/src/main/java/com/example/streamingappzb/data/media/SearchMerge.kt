package com.example.streamingappzb.data.media

import com.example.streamingappzb.domain.model.MediaItem

/**
 * Merges the two search sources.
 *
 * Extracted from the repository because the property that matters — *nothing is ever
 * dropped* — is worth testing on its own. An earlier version advanced the second list at
 * half speed and silently lost half the anime results whenever TMDB returned nothing.
 */
object SearchMerge {

    /** Two film/series results for every anime result. */
    const val PRIMARY_PER_SECONDARY = 2

    /**
     * Interleaves rather than concatenating, so an anime match is never buried twenty rows
     * below a weak film match for the same word. Both lists drain completely: whichever
     * runs out first, the other continues to the end.
     */
    fun interleave(primary: List<MediaItem>, secondary: List<MediaItem>): List<MediaItem> {
        if (primary.isEmpty()) return secondary.distinctBy { it.key }
        if (secondary.isEmpty()) return primary.distinctBy { it.key }

        val out = ArrayList<MediaItem>(primary.size + secondary.size)
        var p = 0
        var s = 0
        while (p < primary.size || s < secondary.size) {
            repeat(PRIMARY_PER_SECONDARY) { if (p < primary.size) out.add(primary[p++]) }
            if (s < secondary.size) out.add(secondary[s++])
        }
        return out.distinctBy { it.key }
    }
}
