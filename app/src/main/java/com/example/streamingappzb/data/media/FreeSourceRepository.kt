package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.remote.archive.ArchiveApi
import com.example.streamingappzb.data.remote.archive.ArchiveFileDto
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType

/**
 * Where a full title comes from, when it can be played in-app at all.
 *
 * Several things implement this and they are tried in order by
 * [CompositeFreeSourceRepository]: the viewer's own files first, then the Internet
 * Archive, then an opt-in sample. Anything none of them resolve routes to its provider
 * instead — see [com.example.streamingappzb.domain.usecase.ResolvePlaybackUseCase].
 *
 * The seam is the point. Every screen asks one question — *is there a source for this?* —
 * and none of them care which implementation answered, so adding a source is adding one
 * class and one line of wiring.
 */
fun interface FreeSourceRepository {
    /** A playable source for this title, or null when this source has none. */
    suspend fun sourceFor(item: MediaItem): PlayableSource?

    /**
     * A source for one episode of a series.
     *
     * Defaulted to the title-level lookup because most sources cannot do better: the
     * Archive matches uploads, not episode files, and the sample is the sample. The two
     * that genuinely hold per-episode media — the viewer's own files and a media server —
     * override this and honour [episode] exactly.
     */
    suspend fun sourceFor(item: MediaItem, episode: EpisodeRef?): PlayableSource? =
        sourceFor(item)
}

/**
 * One episode, as (season, episode) in the catalogue's own numbering.
 *
 * TMDB, Jellyfin and scene-named files all agree on this pair, which is what makes it a
 * safe join key everywhere at once.
 */
data class EpisodeRef(val season: Int, val episode: Int)

/**
 * @param openSearch read per lookup, not captured once: it is a setting the viewer can
 *   change from the library sheet, and the next title screen should honour the new value
 *   without the app being restarted.
 */
class ArchiveFreeSourceRepository(
    private val api: ArchiveApi,
    private val openSearch: () -> Boolean = { false },
) : FreeSourceRepository {

    override suspend fun sourceFor(item: MediaItem): PlayableSource? {
        val open = openSearch()

        if (!open) {
            // Only films, and only ones old enough that a public-domain match is plausible.
            // Querying the Archive for this year's blockbuster wastes a call and, worse,
            // risks matching an unrelated upload with a similar name.
            if (item.type != MediaType.Movie) return null
            val year = item.year ?: return null
            if (year > PUBLIC_DOMAIN_CUTOFF) return null
        }

        val response = runCatching {
            api.search(query = buildQuery(item.title, open), rows = SEARCH_ROWS)
        }.getOrNull() ?: return null

        // Title must genuinely match. The Archive's search is fuzzy and will happily
        // return a documentary *about* the film, which is not the film — so every result
        // is checked, not just the first. Testing only the top hit threw away the whole
        // lookup whenever an unrelated upload happened to outrank the film itself.
        val candidates = response.response?.docs.orEmpty()
            .filter { it.identifier.isNotBlank() && titlesMatch(it.title.orEmpty(), item.title) }

        // An episode is a fraction of a feature's size, so the floor that keeps a trailer
        // from winning on a film would reject every real television upload.
        val minimumBytes = if (item.type.isSeries) MIN_EPISODE_BYTES else MIN_FEATURE_BYTES

        for (doc in candidates) {
            val files = runCatching { api.metadata(doc.identifier) }.getOrNull() ?: continue
            val playable = pickStream(files.files.orEmpty(), minimumBytes) ?: continue

            return PlayableSource(
                url = "$DOWNLOAD_BASE${doc.identifier}/${playable.name}",
                type = StreamType.Progressive,
                // The film's name, not the upload's. An Archive title is whatever the
                // uploader typed — the player header read "Nosferatu Il Vampiro Film
                // Completo…" truncated, where the viewer tapped a poster saying
                // "Nosferatu". Provenance is the attribution line's job.
                label = item.title,
                // Open search reaches uploads whose provenance nobody has reviewed, so it
                // must not claim the one thing the curated collections are evidence of.
                // Captioning an arbitrary upload "Public domain" would be the app
                // asserting a rights position it has no basis for.
                attribution = if (open) {
                    "Internet Archive · uploader-submitted"
                } else {
                    "Public domain · Internet Archive"
                },
                // Archive derivatives are `.mp4` by name but an open search also returns
                // Matroska and AVI, which must not be announced as MP4.
                mimeType = null,
            )
        }
        return null
    }

    /**
     * Chooses which of an item's files to stream.
     *
     * An Archive film is typically several encodings of the same reel: a preservation
     * master of many gigabytes, one or two web derivatives, and often a short excerpt.
     * Taking the first playable name — as this did — could equally hand the player a 12 GB
     * master or a thirty-second clip.
     *
     * Preference order is the Archive's own streaming derivative, then any other MP4,
     * largest first so a trailer-length excerpt loses to the feature. Files too small to
     * be a feature are dropped outright.
     */
    private fun pickStream(
        files: List<ArchiveFileDto>,
        minimumBytes: Long,
    ): ArchiveFileDto? = files
        .filter { file ->
            val name = file.name.orEmpty()
            name.isNotBlank() && PLAYABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
        }
        // Only a *known* undersized file is excluded. Treating an absent size as zero
        // discarded every file on an item that does not report one, which refuses a
        // perfectly playable film rather than erring toward offering it.
        .filterNot { file ->
            val size = file.size?.toLongOrNull()
            size != null && size < minimumBytes
        }
        .minWithOrNull(
            compareBy<ArchiveFileDto> { if (it.isArchiveWebDerivative) 0 else 1 }
                .thenByDescending { it.size?.toLongOrNull() ?: 0L },
        )

    /**
     * The Archive's canonical streamable encode, e.g. `format: "512Kb MPEG4"`.
     *
     * Preferred over the largest MP4 because it is encoded for progressive playback at a
     * bitrate a phone on mobile data can actually sustain.
     */
    private val ArchiveFileDto.isArchiveWebDerivative: Boolean
        get() = format?.contains("512kb", ignoreCase = true) == true

    /**
     * Deliberately no `year:` clause.
     *
     * The Archive's `year` field is the *item's* year — when an upload was published, or
     * whatever the uploader typed — not the film's release year, and it is frequently
     * absent. Constraining it to the release year ±1 excluded essentially every real
     * upload: a 1922 film is typically catalogued under the year it was digitised.
     *
     * The public-domain guarantee does not depend on this. It comes from the TMDB release
     * year checked in [sourceFor] before this query is built, which is the authoritative
     * date and the right place for the test.
     */
    private fun buildQuery(title: String, open: Boolean): String {
        val safe = title.replace("\"", " ").trim()
        val base = "title:(\"$safe\") AND mediatype:(movies)"
        // `mediatype:(movies)` is kept even in open mode: it is the Archive's bucket for
        // all moving images including television, and dropping it returns texts and audio
        // recordings that share a name with the film.
        return if (open) base else "$base AND collection:($CURATED_COLLECTIONS)"
    }

    /**
     * Loose but not permissive.
     *
     * Exact normalised equality was too tight: the Archive catalogues the same film as
     * "Nosferatu (1922)" or under its full original title, and both lost to a plain
     * "Nosferatu". A prefix test accepts those while still rejecting the documentary
     * *about* a film, because "the making of nosferatu" does not start with "nosferatu".
     *
     * The length floor stops a two-letter title prefix-matching half the collection.
     */
    private fun titlesMatch(candidate: String, wanted: String): Boolean {
        val a = normalise(candidate)
        val b = normalise(wanted)
        if (a == b) return true
        if (b.length < MIN_PREFIX_MATCH_CHARS || !a.startsWith(b)) return false

        // Whatever the candidate adds after the title must not mark it as something
        // *about* the film. The prefix rule was written to defeat "The Making of X", which
        // it does because that does not start with "x" — but it waves through "X Official
        // Trailer", which does. Verified on-device: a search for a current release
        // resolved to the trailer item and offered it as the feature, under a Play button.
        val remainder = a.drop(b.length)
        return NON_FEATURE_MARKERS.none { remainder.contains(it) }
    }

    private fun normalise(value: String): String = value
        .lowercase()
        .replace(Regex("^(the|a|an)\\s+"), "")
        .replace(Regex("[^a-z0-9]"), "")

    companion object {
        /**
         * Films first published before 1930 are comfortably out of copyright in every
         * jurisdiction this app ships to. The real US boundary moves each year and is
         * later than this; the conservative line is deliberate — a false positive here
         * would mean streaming something we have no right to.
         *
         * Public because `RowSource.PublicDomain` has to pin its date ceiling to exactly
         * this year. A row that reached further than the resolver would be a row of titles
         * whose play button silently falls through to a trailer.
         */
        const val PUBLIC_DOMAIN_CUTOFF = 1929

        /** The same boundary as a TMDB `primary_release_date.lte` value. */
        const val PUBLIC_DOMAIN_CUTOFF_DATE = "$PUBLIC_DOMAIN_CUTOFF-12-31"

        const val DOWNLOAD_BASE = "https://archive.org/download/"

        /**
         * `.ogv` is deliberately absent. Android guarantees no Theora decoder, so an Ogg
         * file reaches ExoPlayer and fails to render — which looks like a broken player
         * rather than an unsupported file, and the fallback never runs because a source
         * *was* found.
         *
         * Matroska, WebM and AVI are here because an open search reaches items outside the
         * curated collections, which are not limited to the Archive's own MP4 derivatives
         * and are very often `.mkv` or `.avi`. All three have guaranteed or shipped
         * extractors in Media3 for the codecs they realistically carry.
         */
        val PLAYABLE_EXTENSIONS = listOf(".mp4", ".m4v", ".mkv", ".webm", ".avi")

        /**
         * 40 MB. Below this it is an excerpt, a trailer or a title card, not the feature —
         * Archive items routinely carry all three alongside the film.
         */
        const val MIN_FEATURE_BYTES = 40L * 1024 * 1024

        /**
         * 5 MB for an episode.
         *
         * A 22-minute episode at a web bitrate is a fraction of a feature, so the feature
         * floor would reject the whole of television. Low enough to admit a short episode,
         * high enough to still exclude a title card or a thumbnail strip.
         */
        const val MIN_EPISODE_BYTES = 5L * 1024 * 1024

        /**
         * Enough candidates that the film can lose the top slot to an unrelated upload and
         * still be found — [sourceFor] checks every result, not just the first.
         */
        const val SEARCH_ROWS = 15

        /** Below this, a prefix match is meaningless. */
        const val MIN_PREFIX_MATCH_CHARS = 4

        /**
         * Words that, appearing *after* an exact title match, mean the item is about the
         * film rather than being it.
         *
         * Normalised form — no spaces or punctuation — because that is what they are
         * compared against. The size floor in [pickStream] is not a substitute: a 4K
         * trailer clears 40 MB comfortably, so size alone cannot tell them apart.
         */
        val NON_FEATURE_MARKERS = listOf(
            "trailer", "teaser", "tvspot", "promo", "preview", "sneakpeek", "firstlook",
            "clip", "scene", "featurette", "behindthescenes", "makingof", "bts",
            "review", "reaction", "recap", "explained", "breakdown", "analysis",
            "interview", "premiere", "redcarpet", "bloopers", "gagreel", "deletedscenes",
            "soundtrack", "score", "commentary", "podcast", "ending", "postcredit",
        )

        /**
         * The collections the match is confined to, and the app's real legal guard.
         *
         * Relaxing the title test was necessary to find anything at all, but it also let
         * arbitrary re-uploads win: searching for Nosferatu returned
         * `ytdown-you-tube-nosferatu-il-vampiro-film-completo…`, a YouTube rip, which the
         * app would then have captioned "Public domain · Internet Archive" without
         * anything having established that it was.
         *
         * These are curated collections where provenance is reviewed. Anyone can upload
         * into `opensource_movies` or `community`, so neither is here. Filtering in the
         * query rather than on the response also avoids parsing `collection`, which the
         * search API returns as a bare string for one value and an array for several.
         */
        const val CURATED_COLLECTIONS =
            "feature_films OR silent_films OR publicdomainmovies OR " +
                "classic_movies OR film_noir OR prelinger OR sf_shorts"
    }
}

/** Used when no free-source lookup is wanted, e.g. in tests. */
object NoFreeSources : FreeSourceRepository {
    override suspend fun sourceFor(item: MediaItem): PlayableSource? = null
}
