package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.WatchOffer
import com.example.streamingappzb.domain.watch.ProviderCatalogue
import java.net.URLEncoder

/**
 * Turns a watch offer into somewhere to actually send the viewer.
 *
 * The ordering of attempts is the whole point, and it is decided here rather than in a
 * fragment so it is testable:
 *
 * 1. The service's own app, if installed.
 * 2. The service's web search for this title.
 * 3. TMDB's JustWatch link, which is also the attribution their terms require.
 *
 * Whether the app is installed is the one thing this cannot know, so it is asked of the
 * caller through [isInstalled].
 */
class GetWatchOptionsUseCase(
    private val region: com.example.streamingappzb.domain.repository.RegionRepository,
) {

    /** Where a tap on a provider chip should go. */
    data class Handoff(
        /** Launch this package if non-null — the service's own app. */
        val packageName: String?,
        /** Always present: the web destination, and the fallback if the app is missing. */
        val url: String,
        val providerName: String,
    )

    operator fun invoke(
        offer: WatchOffer,
        title: String,
        justWatchLink: String?,
        isInstalled: (String) -> Boolean,
    ): Handoff {
        val encoded = encode(title)
        val target = ProviderCatalogue.target(offer.providerId)

        val packageName = target?.packageName?.takeIf(isInstalled)
        val url = target?.searchUrl(encoded)
            // No entry in the table: JustWatch resolves the long tail properly, and is a
            // better destination than a guessed search URL that may not exist.
            ?: justWatchLink
            ?: ProviderCatalogue.genericSearch(offer.providerName, encoded)

        return Handoff(packageName = packageName, url = url, providerName = offer.providerName)
    }

    /** The region the offers were looked up for, for the "in Pakistan" line. */
    fun currentRegion(): String = region.region()

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
