package com.example.streamingappzb.domain.search

import com.example.streamingappzb.domain.model.Title

/**
 * Spelling-tolerant search (FR-104). Spellings of regional titles drift, so matching runs
 * on a normalised string *and* on a consonant skeleton.
 *
 * [norm] and [skel] are the PRD §6.4 algorithm verbatim, and [substringMatches] is its
 * matching rule verbatim. Both are kept exactly so a server implementing the same rules
 * agrees with the client (PRD §7: "search runs on the server with the same norm and skel
 * rules, and the client matches locally when offline").
 *
 * ### One documented addition
 *
 * The PRD's acceptance checklist requires that `moon lit`, `moonlight` and `monlit` all
 * return *Moonlit Night*. Its own algorithm returns only two of the three:
 *
 * ```
 * norm("Moonlit Night") = "monlitnight"      skel = "mnltnght"
 * norm("moon lit")      = "monlit"           -> substring of "monlitnight"   HIT
 * norm("monlit")        = "monlit"           -> substring of "monlitnight"   HIT
 * norm("moonlight")     = "monlight"         -> not a substring              MISS
 * skel("moonlight")     = "mnlght"           -> not a substring of "mnltnght" MISS
 * ```
 *
 * `moonlight` drops the "it" of "Moonlit", so neither the normalised form nor the
 * skeleton is a contiguous substring. The same hole exists in the design's JS.
 *
 * [skeletonSubsequenceMatches] closes it: the query skeleton must appear in the title
 * skeleton *in order*, skipping at most [MAX_SKIPPED_CONSONANTS] consonants. That is
 * strictly additive — it can only ever add matches, never remove one the PRD's rules
 * found — so the specified behaviour is preserved and the acceptance criterion holds.
 */
object FuzzyMatcher {

    /** At most this many extra consonants may sit inside a skeleton match. */
    const val MAX_SKIPPED_CONSONANTS = 2

    /** Shorter skeletons than this are too loose to match on (the PRD's `k.length >= 3`). */
    const val MIN_SKELETON_LENGTH = 3

    /** PRD §6.4, verbatim: lowercase, letters only, runs of a letter collapsed. */
    fun norm(s: String): String =
        s.lowercase().filter { it in 'a'..'z' }.replace(Regex("(.)\\1+"), "$1")

    /** PRD §6.4, verbatim: the normalised form with vowels (and y) removed. */
    fun skel(s: String): String = norm(s).replace(Regex("[aeiouy]"), "")

    /**
     * Does [title] match [query]?
     *
     * A blank query matches nothing — Search shows genre browse tiles instead of results
     * until something is typed (PRD §6.4).
     */
    fun matches(title: Title, query: String): Boolean {
        if (query.isBlank()) return false
        val n = norm(query)
        val k = skel(query)
        // A query of only punctuation or digits normalises to "" and would otherwise match
        // everything, since "".contains is always true. The design guards this; the PRD's
        // transcription dropped the guard.
        if (n.isEmpty() && k.isEmpty()) return false

        val fields = listOf(title.title, title.genre, title.cast.joinToString(" "))
        if (fields.any { substringMatches(it, n, k) }) return true
        return skeletonSubsequenceMatches(skel(title.title), k)
    }

    /** The PRD's matching rule for a single field. */
    private fun substringMatches(field: String, n: String, k: String): Boolean {
        if (n.isNotEmpty() && norm(field).contains(n)) return true
        return k.length >= MIN_SKELETON_LENGTH && skel(field).contains(k)
    }

    /**
     * Is [query] an in-order subsequence of [target], skipping no more than
     * [MAX_SKIPPED_CONSONANTS] characters between the first and last match? Bounding the
     * span is what keeps this from degenerating into "any letters in any order".
     */
    internal fun skeletonSubsequenceMatches(target: String, query: String): Boolean {
        if (query.length < MIN_SKELETON_LENGTH || target.length < query.length) return false

        // Anchor on each occurrence of the first character: a later anchor can yield a
        // tighter span than the earliest one.
        var anchor = target.indexOf(query[0])
        while (anchor >= 0) {
            var t = anchor
            var q = 0
            while (q < query.length && t < target.length) {
                if (target[t] == query[q]) q++
                t++
            }
            if (q == query.length) {
                val span = t - anchor
                if (span - query.length <= MAX_SKIPPED_CONSONANTS) return true
            }
            anchor = target.indexOf(query[0], anchor + 1)
        }
        return false
    }

    /** Catalogue order is editorial, so matches are returned in it rather than re-ranked. */
    fun filter(titles: List<Title>, query: String): List<Title> =
        if (query.isBlank()) emptyList() else titles.filter { matches(it, query) }
}
