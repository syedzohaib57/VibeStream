package com.example.streamingappzb.domain.watch

/**
 * How to reach each streaming service from a watch offer.
 *
 * **What this can and cannot do.** TMDB tells you *that* Netflix carries a title; it does
 * not give you Netflix's own id for it, and none of these services publish a
 * search-by-third-party-id deep link. So an exact "open this title in Netflix" jump is not
 * available to anyone building on free data — including JustWatch, which resolves it by
 * scraping and maintaining its own id mapping.
 *
 * What is achievable, and what this does:
 *
 * 1. Open the service's app directly if it is installed, at its search screen where the
 *    service publishes one, otherwise at its home screen.
 * 2. Otherwise open the service's web search for the title, which does land on the title
 *    page for every service listed here.
 * 3. Offer TMDB's JustWatch link as well, which is also the attribution their terms
 *    require for provider data.
 *
 * [webSearch] takes the already-encoded title.
 */
data class ProviderTarget(
    val id: Int,
    val name: String,
    /** Android package, when the service ships an app. Null means web only. */
    val packageName: String?,
    /** Web search URL with `%s` for the encoded query. */
    val webSearch: String,
) {
    fun searchUrl(encodedTitle: String): String = webSearch.replace("%s", encodedTitle)
}

/**
 * The services worth special-casing. Everything else falls back to the JustWatch link,
 * which covers the long tail without this table needing to know about it.
 *
 * South Asian services are included on purpose: Hotstar, Zee5 and SonyLIV carry a large
 * share of what viewers in this market actually search for, and a where-to-watch app that
 * only knows the American services is useless here.
 */
object ProviderCatalogue {

    val TARGETS: List<ProviderTarget> = listOf(
        ProviderTarget(8, "Netflix", "com.netflix.mediaclient", "https://www.netflix.com/search?q=%s"),
        ProviderTarget(9, "Amazon Prime Video", "com.amazon.avod.thirdpartyclient", "https://www.primevideo.com/search?phrase=%s"),
        ProviderTarget(119, "Amazon Prime Video", "com.amazon.avod.thirdpartyclient", "https://www.primevideo.com/search?phrase=%s"),
        ProviderTarget(337, "Disney Plus", "com.disney.disneyplus", "https://www.disneyplus.com/search?q=%s"),
        ProviderTarget(350, "Apple TV Plus", "com.apple.atve.androidtv.appletv", "https://tv.apple.com/search?term=%s"),
        ProviderTarget(2, "Apple TV", "com.apple.atve.androidtv.appletv", "https://tv.apple.com/search?term=%s"),
        ProviderTarget(3, "Google Play Movies", "com.google.android.videos", "https://play.google.com/store/search?q=%s&c=movies"),
        ProviderTarget(192, "YouTube", "com.google.android.youtube", "https://www.youtube.com/results?search_query=%s"),
        ProviderTarget(283, "Crunchyroll", "com.crunchyroll.crunchyroid", "https://www.crunchyroll.com/search?q=%s"),
        ProviderTarget(1968, "Crunchyroll", "com.crunchyroll.crunchyroid", "https://www.crunchyroll.com/search?q=%s"),
        ProviderTarget(73, "Tubi", "com.tubitv", "https://tubitv.com/search/%s"),
        ProviderTarget(300, "Pluto TV", "tv.pluto.android", "https://pluto.tv/en/search/details?q=%s"),
        ProviderTarget(613, "Freevee", "com.amazon.avod.thirdpartyclient", "https://www.amazon.com/gp/video/search?phrase=%s"),
        ProviderTarget(531, "Paramount Plus", "com.cbs.ca", "https://www.paramountplus.com/search/?q=%s"),
        ProviderTarget(386, "Peacock", "com.peacocktv.peacockandroid", "https://www.peacocktv.com/search?q=%s"),
        ProviderTarget(1899, "Max", "com.wbd.stream", "https://www.max.com/search?q=%s"),
        ProviderTarget(384, "HBO Max", "com.wbd.stream", "https://www.max.com/search?q=%s"),
        ProviderTarget(11, "MUBI", "com.mubi", "https://mubi.com/search/films?query=%s"),
        ProviderTarget(122, "Hotstar", "in.startv.hotstar", "https://www.hotstar.com/in/explore?search_query=%s"),
        ProviderTarget(232, "Zee5", "com.graymatrix.did", "https://www.zee5.com/search?q=%s"),
        ProviderTarget(237, "SonyLIV", "com.sonyliv", "https://www.sonyliv.com/search?searchTerm=%s"),
        ProviderTarget(220, "JioCinema", "com.jio.media.ondemand", "https://www.jiocinema.com/search/%s"),
        ProviderTarget(121, "Voot", "com.tv.v18.viola", "https://www.voot.com/search?q=%s"),
    )

    private val byId: Map<Int, ProviderTarget> = TARGETS.associateBy { it.id }

    fun target(providerId: Int): ProviderTarget? = byId[providerId]

    /** A last-resort web search when the provider is not in the table above. */
    fun genericSearch(providerName: String, encodedTitle: String): String =
        "https://www.google.com/search?q=$encodedTitle+site+$providerName+watch"
}
