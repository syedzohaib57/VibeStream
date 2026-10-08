package com.example.streamingappzb.data.remote.anilist

/**
 * The four GraphQL documents the app sends.
 *
 * They ask for exactly the fields the UI draws. That is not tidiness: AniList rate-limits
 * by request weight, and over-fetching characters and studios on a 30-item row is the
 * quickest way to get throttled during normal browsing.
 */
object AniListQueries {

    private const val ITEM_FIELDS = """
        id
        title { romaji english native }
        coverImage { extraLarge large medium }
        bannerImage
        seasonYear
        startDate { year month day }
        averageScore
        episodes
        format
        genres
        status
    """

    /** Airing this season, most popular first — the "Anime this season" row. */
    val SEASON_NOW = """
        query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}perPage: Int) {
          Page(page: 1, perPage: ${'$'}perPage) {
            media(season: ${'$'}season, seasonYear: ${'$'}year, type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
              $ITEM_FIELDS
              nextAiringEpisode { episode airingAt }
            }
          }
        }
    """.trimIndent()

    /** All-time highest rated — the "Top anime" row. */
    val TOP_RATED = """
        query (${'$'}perPage: Int) {
          Page(page: 1, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: SCORE_DESC, isAdult: false) {
              $ITEM_FIELDS
            }
          }
        }
    """.trimIndent()

    /** Not yet aired, soonest first — anime's half of the Upcoming row. */
    val UPCOMING = """
        query (${'$'}perPage: Int) {
          Page(page: 1, perPage: ${'$'}perPage) {
            media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, isAdult: false) {
              $ITEM_FIELDS
              nextAiringEpisode { episode airingAt }
            }
          }
        }
    """.trimIndent()

    val SEARCH = """
        query (${'$'}search: String, ${'$'}perPage: Int) {
          Page(page: 1, perPage: ${'$'}perPage) {
            media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH, isAdult: false) {
              $ITEM_FIELDS
            }
          }
        }
    """.trimIndent()

    /** One title, with the extras only the detail screen needs. */
    val DETAIL = """
        query (${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            $ITEM_FIELDS
            description(asHtml: false)
            duration
            studios(isMain: true) { nodes { name } }
            nextAiringEpisode { episode airingAt }
            trailer { id site }
            characters(sort: ROLE, perPage: 12) {
              nodes { name { full } image { large medium } }
            }
          }
        }
    """.trimIndent()

    /**
     * AniList's season enum for a calendar month.
     *
     * Their seasons are fixed three-month blocks starting in January, not meteorological
     * or broadcast-year ones, so this is a straight division.
     */
    fun seasonFor(month: Int): String = when (month) {
        in 1..3 -> "WINTER"
        in 4..6 -> "SPRING"
        in 7..9 -> "SUMMER"
        else -> "FALL"
    }
}
