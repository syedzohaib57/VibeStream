package com.example.streamingappzb.data.media

import com.example.streamingappzb.domain.model.MediaItem

/**
 * Reads a media filename back into the fields a catalogue title is identified by.
 *
 * The problem this solves is that a local library is named by whoever ripped it, not by
 * TMDB: `The.Matrix.1999.1080p.BluRay.x264-AMIABLE.mkv` has to be recognised as the same
 * thing as TMDB's `The Matrix` (1999). Nothing here is clever — it is a scene-naming
 * convention that has been stable for twenty years, so parsing it is mostly knowing where
 * the title stops.
 *
 * Kept free of Android types so the matching rules, which are the part that can be subtly
 * wrong, are unit-testable on the JVM.
 */
object TitleMatch {

    /** What a filename claims to be. [season] and [episode] are null for a film. */
    data class Parsed(
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
    ) {
        /** Pre-computed because matching compares it against every catalogue title. */
        val normalised: String = normalise(title)

        val isEpisode: Boolean get() = season != null || episode != null

        /** "S01E04", for the player header. Null when this is not an episode. */
        val episodeCode: String?
            get() = when {
                season != null && episode != null -> "S%02dE%02d".format(season, episode)
                episode != null -> "E%02d".format(episode)
                else -> null
            }
    }

    /**
     * Pulls the title, year and episode numbering out of a filename.
     *
     * The title is everything *before* the first thing that is recognisably not part of a
     * title — an episode code, a year, or a release-quality tag. Taking everything up to
     * the extension instead is what makes a naive matcher fail on every real file: the
     * normalised form of `thematrix1999 1080pblurayx264amiable` matches nothing.
     */
    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.', fileName)
        // Scene names use dots and underscores as word separators. Brackets hold release
        // metadata — `[1080p]`, `[Judas]` — never part of a title.
        val words = base
            .replace(Regex("[._]+"), " ")
            .replace(Regex("[\\[\\](){}]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null
        var titleEnd = words.size

        words.forEachIndexed { index, word ->
            // Each test records where the title stopped, but only the *earliest* such
            // position wins — a year after the episode code must not extend the title back
            // over the code.
            fun stopHere() {
                if (index < titleEnd) titleEnd = index
            }

            episodeCode(word)?.let { (s, e) ->
                if (season == null && episode == null) {
                    season = s
                    episode = e
                    stopHere()
                }
                return@forEachIndexed
            }

            yearIn(word)?.let {
                if (year == null) {
                    year = it
                    stopHere()
                }
                return@forEachIndexed
            }

            if (word.lowercase() in NOISE) stopHere()
        }

        // A file called exactly `1999.mkv` is a title, not a bare year: taking the year
        // would leave nothing to match on at all.
        val title = words.take(titleEnd).joinToString(" ").trim(*TRIM_CHARS)
        if (title.isEmpty()) {
            return Parsed(words.joinToString(" "), null, season, episode)
        }
        return Parsed(title, year, season, episode)
    }

    /**
     * The catalogue side of a comparison, normalised once.
     *
     * Exists because matching runs over the whole library per title screen. Normalising
     * inside [matches] meant three regex replacements against the *same* catalogue title
     * for every file indexed — tens of thousands of regex operations on the main thread for
     * one lookup.
     */
    data class Target(
        val normalised: String,
        val year: Int?,
        val isSeries: Boolean,
    )

    fun target(item: MediaItem): Target = Target(
        normalised = normalise(item.title),
        year = item.year,
        isSeries = item.type.isSeries,
    )

    /** Convenience for a one-off comparison; prefer [target] when looping. */
    fun matches(parsed: Parsed, item: MediaItem): Boolean = matches(parsed, target(item))

    /**
     * Whether a parsed filename is this catalogue title.
     *
     * Deliberately strict on the year and loose on the words. The year is the one field
     * both sides agree on mechanically, so it is the cheap way to keep the four unrelated
     * films called *Crash* apart; the words are typed by hand on one side and localised on
     * the other, so demanding they match exactly rejects real files.
     */
    fun matches(parsed: Parsed, target: Target): Boolean {
        if (!yearsAgree(parsed.year, target)) return false

        val fileTitle = parsed.normalised
        val wanted = target.normalised
        if (fileTitle.isEmpty() || wanted.isEmpty()) return false
        if (fileTitle == wanted) return true

        // A prefix match in either direction, because either side can be the longer one:
        // a file may be `Dune.Part.Two.2024` against TMDB's `Dune: Part Two`, or
        // `Alien.1979` against `Alien: Director's Cut`. The length floor is what stops a
        // three-letter title prefix-matching half the library.
        if (wanted.length >= MIN_PREFIX_CHARS && fileTitle.startsWith(wanted)) return true
        if (fileTitle.length >= MIN_PREFIX_CHARS && wanted.startsWith(fileTitle)) return true
        return false
    }

    /**
     * Release years have to line up, with one year of slack.
     *
     * The slack is not laziness: a film that premieres at a festival in December and opens
     * wide in January is catalogued under one year by TMDB and named for the other by
     * whoever ripped it, and that is common enough that demanding equality loses real
     * matches.
     *
     * A series is exempt. TMDB's year for a show is its *first air date*, so every file
     * from a later season legitimately disagrees with it.
     */
    private fun yearsAgree(fileYear: Int?, target: Target): Boolean {
        if (target.isSeries) return true
        val itemYear = target.year
        if (fileYear == null || itemYear == null) return true
        return kotlin.math.abs(fileYear - itemYear) <= YEAR_SLACK
    }

    /** Lowercase alphanumerics only, leading article dropped. */
    fun normalise(value: String): String = value
        .lowercase()
        .replace("&", "and")
        .replace(Regex("^(the|a|an)\\s+"), "")
        .replace(Regex("[^a-z0-9]"), "")

    /** `S01E02`, `s1e2`, `1x02`, `E04`. Returns season (nullable) to episode. */
    private fun episodeCode(word: String): Pair<Int?, Int>? {
        SEASON_EPISODE.matchEntire(word)?.let { m ->
            return m.groupValues[1].toInt() to m.groupValues[2].toInt()
        }
        CROSS_EPISODE.matchEntire(word)?.let { m ->
            return m.groupValues[1].toInt() to m.groupValues[2].toInt()
        }
        EPISODE_ONLY.matchEntire(word)?.let { m ->
            return null to m.groupValues[1].toInt()
        }
        return null
    }

    /**
     * A four-digit year, bare or parenthesised.
     *
     * Bounded at both ends so a resolution (`2160`) or a bitrate is not read as a year —
     * which would truncate the title at the quality tag and, worse, record a year that
     * then fails [yearsAgree] against every real title.
     */
    private fun yearIn(word: String): Int? {
        val digits = word.trim(*TRIM_CHARS)
        if (!digits.matches(Regex("\\d{4}"))) return null
        return digits.toInt().takeIf { it in FIRST_FILM_YEAR..LAST_PLAUSIBLE_YEAR }
    }

    private val SEASON_EPISODE = Regex("(?i)s(\\d{1,2})\\s*e(\\d{1,3})")
    private val CROSS_EPISODE = Regex("(?i)(\\d{1,2})x(\\d{1,3})")
    private val EPISODE_ONLY = Regex("(?i)e(?:p|pisode)?(\\d{1,3})")

    private val TRIM_CHARS = charArrayOf('-', '–', ':', ',', '.', '\'', '"', ' ')

    /** Films were not made before this, so a smaller number is not a year. */
    private const val FIRST_FILM_YEAR = 1888

    /**
     * Above this a four-digit number is a resolution or a bitrate, not a year. Generous
     * enough to cover an announced-but-unreleased title without reaching 2160.
     */
    private const val LAST_PLAUSIBLE_YEAR = 2099

    private const val YEAR_SLACK = 1

    /** See [matches]. Four characters is where a prefix stops being a coincidence. */
    private const val MIN_PREFIX_CHARS = 4

    /**
     * Tokens that mark the end of a title.
     *
     * Only words that genuinely never appear in one. `Extended`, `Final` and `Part` are
     * all release tags *and* real title words ("Final Destination", "Dune: Part Two"), so
     * none of them are here — the year and the episode code already stop the title before
     * a tag matters in the overwhelming majority of real filenames.
     */
    private val NOISE = setOf(
        // Resolution and source
        "480p", "576p", "720p", "1080p", "1080i", "1440p", "2160p", "4k", "8k", "uhd", "hd",
        "bluray", "blu-ray", "bdrip", "brrip", "bdremux", "remux", "webrip", "web-dl", "webdl",
        "web", "hdrip", "hdtv", "dvdrip", "dvdscr", "dvd", "cam", "ts", "telesync", "r5",
        // Codecs and audio
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "av1", "10bit", "8bit",
        "aac", "aac2", "ac3", "eac3", "dts", "dtshd", "truehd", "atmos", "flac", "mp3",
        // Channel counts are deliberately absent: `5.1` splits to a bare "5", and a bare
        // digit is a title word at least as often as it is an audio layout.
        "ddp5", "dd5", "dd2",
        // Release housekeeping
        "proper", "repack", "internal", "limited", "unrated", "multi", "dual", "dubbed",
        "subbed", "hardsub", "sdr", "hdr", "hdr10", "dv", "imax", "sample",
    )
}
