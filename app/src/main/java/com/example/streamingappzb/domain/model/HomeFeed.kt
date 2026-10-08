package com.example.streamingappzb.domain.model

/**
 * A curated row. The UI prefers a localised strings.xml heading for a [key] it ships and
 * falls back to [fallbackTitle], which carries whatever a real `GET /home` sent for a row
 * it has never seen (PRD §8, Localisation).
 */
data class HomeRow(
    val key: String,
    val fallbackTitle: String,
    val titles: List<Title>,
) {
    /** The `new` row gets the rose dot and "N new" in its trailing slot (PRD §6.1). */
    val isNewRow: Boolean get() = key == KEY_NEW

    /**
     * The `top10` row draws each poster over its rank numeral (§6.1, ranked rows).
     *
     * Rank is the row's existing order — position 1 is the first title — so this is a
     * presentation flag and nothing upstream has to send a score. The row is capped at ten
     * because the numeral only reads as a rank up to there.
     */
    val isRankedRow: Boolean get() = key == KEY_TOP10

    /** Titles shown in a ranked row, however many the row actually carries. */
    val rankedTitles: List<Title> get() = if (isRankedRow) titles.take(MAX_RANK) else titles

    companion object {
        const val KEY_NEW = "new"
        const val KEY_TOP10 = "top10"

        /** Ten is the whole premise of the row. */
        const val MAX_RANK = 10
    }
}

/**
 * Everything Home renders, already filtered by the selected chip.
 *
 * The row cap is enforced when this is built, not in the fragment: Home never shows more
 * than eight rows *including* Continue watching (PRD §8.5, acceptance item 3), and a row
 * left empty by filtering is dropped rather than rendered empty.
 */
data class HomeFeed(
    val hero: Title?,
    val heroProgress: Progress?,
    val continueWatching: List<ContinueItem>,
    val rows: List<HomeRow>,
) {
    companion object {
        /** Continue watching counts against this. */
        const val MAX_ROWS = 8
    }
}

/** The Home category chips (PRD §6.1). A null [kind] is "All". */
data class CategoryChip(
    val key: String,
    val labelRes: Int,
    val kind: Kind?,
) {
    companion object {
        const val KEY_ALL = "all"
    }
}
