package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.MediaDetail
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.Trailer
import com.example.streamingappzb.domain.model.WatchOffer

/**
 * Decides what the primary button on a title actually does.
 *
 * This is the one place the app's legal boundary is expressed in code, so it is a use case
 * rather than a branch inside a fragment:
 *
 * 1. If the title has a [PlayableSource] — public domain, openly licensed, or whatever
 *    licensed source is wired in behind it — play it in full, in our player.
 * 2. Otherwise, if a service carries it, hand off to that service. Cheapest offer first,
 *    so a free or ad-supported option is always preferred over a rental.
 * 3. Otherwise, play the official trailer.
 * 4. Otherwise there is nothing to offer, and the button says so instead of lying.
 *
 * What it never does is synthesise a stream for a licensed title. There is no free API
 * that serves that video, and an app that appears to find one is scraping somebody's
 * service.
 */
class ResolvePlaybackUseCase {

    sealed interface Action {
        /** Full playback, in-app. */
        data class Play(val source: PlayableSource) : Action

        /** Open the service that carries it. */
        data class OpenProvider(val offer: WatchOffer, val fallbackUrl: String?) : Action

        /** The official trailer, in the embedded player. */
        data class PlayTrailer(val trailer: Trailer) : Action

        /** Nothing is available in this region. */
        data object Unavailable : Action
    }

    operator fun invoke(detail: MediaDetail): Action {
        detail.playable?.let { return Action.Play(it) }

        detail.watch.best?.let { offer ->
            return Action.OpenProvider(offer, detail.watch.justWatchLink)
        }

        detail.bestTrailer?.let { return Action.PlayTrailer(it) }

        return Action.Unavailable
    }

    /**
     * The secondary action, which must never repeat the primary one — offering "Trailer"
     * twice, or next to nothing at all, is worse than offering a single button.
     */
    fun secondary(detail: MediaDetail, primary: Action): Action? = when (primary) {
        is Action.Play, is Action.OpenProvider ->
            detail.bestTrailer?.let { Action.PlayTrailer(it) }
        is Action.PlayTrailer, Action.Unavailable -> null
    }
}
