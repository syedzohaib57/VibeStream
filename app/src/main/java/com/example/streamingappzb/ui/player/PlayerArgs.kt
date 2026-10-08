package com.example.streamingappzb.ui.player

import com.example.streamingappzb.domain.model.PlayableSource

/**
 * What the player was asked to play.
 *
 * The two cases are genuinely different and were previously conflated: a catalogue episode
 * has an id, a runtime, an ad schedule, a next episode and a progress row, while a
 * [PlayableSource] resolved from the free-source layer has a URL and nothing else. Modelling
 * them as one set of nullable ints is what pushed full playback onto `TrailerActivity`
 * instead of this player.
 *
 * A single non-null argument also keeps the Koin definition honest — `parametersOf` with a
 * nullable value resolves by type and is a trap.
 */
sealed interface PlayerArgs {

    val positionSeconds: Int

    /** False when the intent carried nothing playable, so the Activity can just exit. */
    val isValid: Boolean
        get() = when (this) {
            is Catalog -> titleId > 0
            is Source -> source.url.isNotBlank()
        }

    /** An episode from the sample catalogue, with everything the full pipeline needs. */
    data class Catalog(
        val titleId: Int,
        val episode: Int,
        override val positionSeconds: Int = 0,
    ) : PlayerArgs

    /**
     * A source this app may legally stream — public domain or openly licensed.
     *
     * Carries no ads: the schedule is the catalogue's, and a public-domain film has no ad
     * inventory attached to it. Carries no progress row either, because the progress table
     * is keyed by catalogue id and a TMDB id would collide with one.
     */
    data class Source(
        val source: PlayableSource,
        override val positionSeconds: Int = 0,
    ) : PlayerArgs

    companion object {
        /** A malformed or missing intent. The player shows its fatal state and exits. */
        val Invalid = Catalog(titleId = -1, episode = 1)
    }
}
