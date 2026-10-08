package com.example.streamingappzb.server

/**
 * Server-side search, kept character-for-character identical to the client's
 * `com.example.streamingappzb.domain.search.FuzzyMatcher` (PRD §6.4 / §7: "search runs on
 * the server with the same norm and skel rules, and the client matches locally when
 * offline"). The only difference is that [matches] takes the three searchable fields
 * directly rather than a `Title`, because this module has no `Title` type.
 */
object Search {

    /** At most this many extra consonants may sit inside a skeleton match. */
    const val MAX_SKIPPED_CONSONANTS = 2

    /** Shorter skeletons than this are too loose to match on. */
    const val MIN_SKELETON_LENGTH = 3

    /** Lowercase, letters only, runs of a letter collapsed. */
    fun norm(s: String): String =
        s.lowercase().filter { it in 'a'..'z' }.replace(Regex("(.)\\1+"), "$1")

    /** The normalised form with vowels (and y) removed. */
    fun skel(s: String): String = norm(s).replace(Regex("[aeiouy]"), "")

    fun matches(title: String, genre: String, cast: String, query: String): Boolean {
        if (query.isBlank()) return false
        val n = norm(query)
        val k = skel(query)
        if (n.isEmpty() && k.isEmpty()) return false

        val fields = listOf(title, genre, cast)
        if (fields.any { substringMatches(it, n, k) }) return true
        return skeletonSubsequenceMatches(skel(title), k)
    }

    private fun substringMatches(field: String, n: String, k: String): Boolean {
        if (n.isNotEmpty() && norm(field).contains(n)) return true
        return k.length >= MIN_SKELETON_LENGTH && skel(field).contains(k)
    }

    internal fun skeletonSubsequenceMatches(target: String, query: String): Boolean {
        if (query.length < MIN_SKELETON_LENGTH || target.length < query.length) return false

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
}
