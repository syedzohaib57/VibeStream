package com.example.streamingappzb.data.media

import com.example.streamingappzb.domain.model.MediaRow
import com.example.streamingappzb.domain.model.MediaType

/** Which endpoint a row comes from. */
enum class RowSource {
    Trending,

    /** Trending over a day rather than a week — it moves enough to be its own row. */
    TrendingToday,
    PopularMovies,
    PopularTv,
    TopRatedMovies,
    TopRatedTv,

    /** Series with an episode in the current week, from `tv/on_the_air`. */
    OnTheAir,
    NowPlaying,
    UpcomingMovies,
    UpcomingTv,

    /**
     * Films published early enough to be out of copyright, newest-rated first.
     *
     * This is the only row the app can *play* rather than hand off: its date ceiling is
     * pinned to `ArchiveFreeSourceRepository.PUBLIC_DOMAIN_CUTOFF`, so every title in it is
     * one the Internet Archive plausibly holds and we may legally stream. Moving one
     * boundary without the other either breaks playback or overreaches it.
     */
    PublicDomain,

    /** `/discover` seeded with a genre, with a vote floor. See `MediaRepositoryImpl.fetch`. */
    Documentaries,
    Animation,
    AnimeSeason,
    AnimeTop,
    AnimeUpcoming,
}

/**
 * A row's identity, heading and source.
 *
 * Headings are plain English here and resolved to string resources by the UI, because the
 * data layer must not reach for `Context` to fetch a string.
 */
data class RowSpec(
    val key: String,
    val title: String,
    val source: RowSource,
)

/**
 * What each tab shows.
 *
 * Ordering is the editorial decision: every tab leads with what is moving right now, then
 * what is coming, and only then the evergreen lists. Upcoming sits second because
 * "what's next" is one of the two questions this app exists to answer.
 *
 * Each list is longer than [MediaType] tabs can display — `MediaFeed.MAX_ROWS` trims it.
 * That is deliberate: a row whose endpoint returns nothing is dropped, so the overflow is
 * what keeps the screen full when one source is empty for a region.
 */
object RowSpecs {

    private val ALL = listOf(
        RowSpec(MediaRow.KEY_TRENDING, "Trending this week", RowSource.Trending),
        RowSpec(MediaRow.KEY_UPCOMING, "Coming soon", RowSource.UpcomingMovies),
        // Rendered as the ranked Top 10 row. The source is unchanged — TMDB's trending/day
        // already comes back most-popular first, so the rank is the order it arrives in.
        RowSpec(MediaRow.KEY_TOP10, "Top 10 today", RowSource.TrendingToday),
        RowSpec(MediaRow.KEY_ANIME_SEASON, "Anime this season", RowSource.AnimeSeason),
        // Sits above the evergreen lists because it is the only row that plays in-app.
        RowSpec(MediaRow.KEY_PUBLIC_DOMAIN, "Free to watch now", RowSource.PublicDomain),
        RowSpec(MediaRow.KEY_POPULAR_MOVIES, "Popular films", RowSource.PopularMovies),
        RowSpec(MediaRow.KEY_POPULAR_TV, "Popular series", RowSource.PopularTv),
        RowSpec(MediaRow.KEY_NOW_PLAYING, "In cinemas now", RowSource.NowPlaying),
        RowSpec(MediaRow.KEY_TOP_RATED, "Top rated", RowSource.TopRatedMovies),
        RowSpec(MediaRow.KEY_ON_THE_AIR, "On air this week", RowSource.OnTheAir),
    )

    private val MOVIES = listOf(
        RowSpec(MediaRow.KEY_POPULAR_MOVIES, "Popular films", RowSource.PopularMovies),
        RowSpec(MediaRow.KEY_UPCOMING, "Coming soon", RowSource.UpcomingMovies),
        RowSpec(MediaRow.KEY_NOW_PLAYING, "In cinemas now", RowSource.NowPlaying),
        RowSpec(MediaRow.KEY_TOP_RATED, "Top rated films", RowSource.TopRatedMovies),
        RowSpec(MediaRow.KEY_PUBLIC_DOMAIN, "Free to watch now", RowSource.PublicDomain),
        RowSpec(MediaRow.KEY_TRENDING, "Trending this week", RowSource.Trending),
        RowSpec(MediaRow.KEY_ANIMATION, "Animated", RowSource.Animation),
        RowSpec(MediaRow.KEY_DOCUMENTARIES, "Documentaries", RowSource.Documentaries),
    )

    private val TV = listOf(
        RowSpec(MediaRow.KEY_POPULAR_TV, "Popular series", RowSource.PopularTv),
        RowSpec(MediaRow.KEY_ON_THE_AIR, "On air this week", RowSource.OnTheAir),
        RowSpec(MediaRow.KEY_UPCOMING, "New seasons coming", RowSource.UpcomingTv),
        RowSpec(MediaRow.KEY_TOP_RATED, "Top rated series", RowSource.TopRatedTv),
        RowSpec(MediaRow.KEY_TRENDING, "Trending this week", RowSource.Trending),
    )

    private val ANIME = listOf(
        RowSpec(MediaRow.KEY_ANIME_SEASON, "Airing this season", RowSource.AnimeSeason),
        RowSpec(MediaRow.KEY_UPCOMING, "Coming soon", RowSource.AnimeUpcoming),
        RowSpec(MediaRow.KEY_ANIME_TOP, "Highest rated anime", RowSource.AnimeTop),
    )

    fun forTab(tab: MediaType?): List<RowSpec> = when (tab) {
        null -> ALL
        MediaType.Movie -> MOVIES
        MediaType.Tv -> TV
        MediaType.Anime -> ANIME
    }

    /** For a one-off fetch that is not part of a tab, e.g. the Upcoming screen. */
    fun of(source: RowSource): RowSpec = ALL.plus(MOVIES).plus(TV).plus(ANIME)
        .firstOrNull { it.source == source }
        ?: RowSpec(source.name.lowercase(), source.name, source)
}
