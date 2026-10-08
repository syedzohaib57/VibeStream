package com.example.streamingappzb.data.media

import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType

/**
 * Asks each source in turn and takes the first answer.
 *
 * Order is the whole design, and it is cheapest-and-best-first rather than arbitrary:
 *
 * 1. [LocalFreeSourceRepository] — the viewer's own file. No network, full quality, and
 *    the only source that is certainly the title they asked for.
 * 2. [ArchiveFreeSourceRepository] — public-domain and openly-licensed film, over HTTP.
 * 3. [SampleFallbackSourceRepository] — opt-in, and not the real title.
 *
 * Every source is isolated. One throwing — a revoked folder permission, the Archive being
 * down — costs that source and lets the next one answer, because the alternative is that
 * a transient failure in the first source hides a perfectly good second one.
 */
class CompositeFreeSourceRepository(
    private val sources: List<FreeSourceRepository>,
) : FreeSourceRepository {

    override suspend fun sourceFor(item: MediaItem): PlayableSource? =
        sourceFor(item, episode = null)

    override suspend fun sourceFor(item: MediaItem, episode: EpisodeRef?): PlayableSource? {
        for (source in sources) {
            val resolved = runCatching { source.sourceFor(item, episode) }.getOrNull()
            if (resolved != null) return resolved
        }
        return null
    }
}

/**
 * Plays a bundled sample when nothing real resolved, so the play button never dead-ends.
 *
 * Off unless [AppPrefs.sampleFallback] is on, and it says what it is in the attribution
 * line, because the one thing worse than a disabled button is a button that looks like it
 * will play the film and plays something else.
 *
 * What it is genuinely for: the player pipeline — the quality ladder, the ad schedule, Up
 * next, the seek rail, MediaSession, progress writes — can only be exercised against a
 * title that has a source, and before this that was a few dozen pre-1930 films. With this
 * on, any title in the catalogue drives the whole stack.
 *
 * The samples are openly-licensed Blender films and the standard reference streams, which
 * is also why they are safe to ship: they are the same assets the sample catalogue in
 * `assets/catalog.json` already uses.
 */
class SampleFallbackSourceRepository(
    /** Read per lookup, so toggling the setting does not need a restart. */
    private val enabled: () -> Boolean,
) : FreeSourceRepository {

    override suspend fun sourceFor(item: MediaItem): PlayableSource? {
        if (!enabled()) return null

        // Keyed off the id rather than picked at random, so a title plays the same sample
        // every time. A stream that changes between launches reads as a bug, and it would
        // also invalidate the saved position from the previous session.
        val sample = SAMPLES[Math.floorMod(item.id, SAMPLES.size)]

        return PlayableSource(
            url = sample.url,
            type = sample.type,
            label = item.title,
            attribution = "Sample stream · not the real title",
        )
    }

    private data class Sample(val url: String, val type: StreamType)

    private companion object {
        /**
         * Deliberately a mix of DASH, HLS and progressive. A single container would leave
         * two thirds of the playback paths untested, which is the opposite of the point.
         */
        val SAMPLES = listOf(
            // Tears of Steel (Blender Foundation, CC-BY), clear DASH.
            Sample("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd", StreamType.Dash),
            // Big Buck Bunny (Blender Foundation, CC-BY), multi-variant HLS.
            Sample("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", StreamType.Hls),
            Sample(
                "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
                StreamType.Hls,
            ),
            Sample(
                "https://storage.googleapis.com/gvabox/media/samples/stock.mp4",
                StreamType.Progressive,
            ),
        )
    }
}
