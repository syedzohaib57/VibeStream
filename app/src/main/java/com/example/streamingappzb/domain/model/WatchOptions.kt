package com.example.streamingappzb.domain.model

/**
 * How a title is monetised on a given service, in the order a viewer cares about.
 *
 * [Free] and [Ads] come first deliberately: an app whose whole promise is "watch without
 * spending" must lead with the option that costs nothing.
 */
enum class OfferType {
    /** Free, no advertising, no account. */
    Free,

    /** Free but ad-supported — Tubi, Pluto, YouTube with ads. */
    Ads,

    /** Included with a subscription the viewer may already have. */
    Subscription,

    /** Paid rental. */
    Rent,

    /** Paid purchase. */
    Buy,
    ;

    val costsNothing: Boolean get() = this == Free || this == Ads

    companion object {
        /** TMDB's key for each bucket in `/watch/providers`. */
        fun fromTmdb(key: String): OfferType? = when (key) {
            "free" -> Free
            "ads" -> Ads
            "flatrate" -> Subscription
            "rent" -> Rent
            "buy" -> Buy
            else -> null
        }

        /** Cheapest first — the order the title screen lists them in. */
        val DISPLAY_ORDER = listOf(Free, Ads, Subscription, Rent, Buy)
    }
}

/** One service carrying the title, e.g. Netflix on subscription. */
data class WatchOffer(
    val providerId: Int,
    val providerName: String,
    val logoPath: String?,
    val offerType: OfferType,
)

/**
 * Everything known about where a title can be watched in one region.
 *
 * [justWatchLink] is TMDB's own attribution link. TMDB's terms require that the provider
 * data be attributed to JustWatch, so it is not optional decoration — the title screen
 * always shows it when offers are present.
 */
data class WatchOptions(
    val region: String,
    val offers: List<WatchOffer>,
    val justWatchLink: String?,
) {
    val isEmpty: Boolean get() = offers.isEmpty()

    /** Grouped and ordered for display, cheapest bucket first, empty buckets dropped. */
    val byType: List<Pair<OfferType, List<WatchOffer>>>
        get() = OfferType.DISPLAY_ORDER
            .map { type -> type to offers.filter { it.offerType == type }.distinctBy { it.providerId } }
            .filter { (_, list) -> list.isNotEmpty() }

    /** True when at least one option costs the viewer nothing. */
    val hasFreeOption: Boolean get() = offers.any { it.offerType.costsNothing }

    /** The single option to put on the primary button. */
    val best: WatchOffer?
        get() = OfferType.DISPLAY_ORDER.firstNotNullOfOrNull { type ->
            offers.firstOrNull { it.offerType == type }
        }

    companion object {
        fun empty(region: String) = WatchOptions(region, emptyList(), null)
    }
}
