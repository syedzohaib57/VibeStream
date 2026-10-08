package com.example.streamingappzb.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.download.DownloadEngine
import com.example.streamingappzb.domain.model.AdPlaybackState
import com.example.streamingappzb.domain.model.AdSchedule
import com.example.streamingappzb.domain.model.Episode
import com.example.streamingappzb.domain.model.OfflinePlayback
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.model.isFinished

/** What the player screen draws. */
data class PlaybackState(
    val title: Title? = null,
    val episode: Episode? = null,
    val nextEpisode: Episode? = null,
    /**
     * Set instead of [title] when playing a free source, which has no catalogue row. The
     * two are mutually exclusive: exactly one of them names what is on screen.
     */
    val sourceLabel: String? = null,
    /** e.g. "Public domain · Internet Archive". Shown under the controls, not optional. */
    val attribution: String? = null,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ad: AdPlaybackState = AdPlaybackState.IDLE,
    val adBreakFractions: List<Float> = emptyList(),
    /** Ad-free because it was monetised at download time (FR-505). */
    val playingFromDownload: Boolean = false,
    /** Seconds left on the Up next countdown, or null when the card is not showing. */
    val upNextSeconds: Int? = null,
    val failed: Boolean = false,
) {
    val inAd: Boolean get() = ad.inAd

    /** Controls are inert and dimmed while an ad plays (PRD §6.3). */
    val controlsEnabled: Boolean get() = !inAd
}

/**
 * Owns the ExoPlayer and everything that has to stay in step with it: ads, the rung,
 * subtitles, Up next, and progress writes.
 *
 * Mirrors the single 1 Hz clock in the design's `index.html`, which resolves in the same
 * order — **ad, then Up next, then position** — so the three cannot fight over a tick.
 *
 * The player reads through the download cache, which is what lets a downloaded episode
 * play with no network at all.
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    context: Context,
    engine: DownloadEngine,
    private val analytics: Analytics,
) {

    private val adController = AdController(analytics)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(DefaultRenderersFactory(context))
        // Cache-backed, so an offline copy is served without a network request.
        .setMediaSourceFactory(DefaultMediaSourceFactory(engine.playbackDataSourceFactory))
        .build()

    private var title: Title? = null
    private var episode: Episode? = null
    private var nextEpisode: Episode? = null
    private var fromDownload = false

    /** Set by [loadSource] instead of [title]; see [PlaybackState.sourceLabel]. */
    private var sourceLabel: String? = null
    private var attribution: String? = null

    /**
     * The media's real duration, once the player knows it.
     *
     * The catalogue's runtime drives sizing and metadata ("39 min · 99 MB") and is what the
     * viewer is quoted, but every *playback* mechanic — the seek rail, the Up next lead,
     * the finished rule, and where the ad breaks fall — has to key off the media actually
     * loaded. In production the two agree; when they do not, trusting the catalogue would
     * put the rail's end past the real end of the stream.
     */
    private var mediaDurationSeconds = 0
    private var upNextSeconds: Int? = null
    private var upNextElapsedMillis = 0L
    private var upNextSuppressed = false
    private var lastSavedPositionSeconds = -1
    private var failed = false
    private var startPositionSeconds = 0

    /** The catalogue runtime until the player reports the real one. */
    private val effectiveDurationSeconds: Int
        get() = if (mediaDurationSeconds > 0) mediaDurationSeconds else episode?.durationSeconds ?: 0

    var state: PlaybackState = PlaybackState()
        private set

    var onStateChanged: ((PlaybackState) -> Unit)? = null

    /** Fired when the episode is watched through, so the caller can advance or exit. */
    var onEpisodeFinished: (() -> Unit)? = null

    /** Fired every [Progress.SAVE_INTERVAL_SECONDS] and on pause, with the live position. */
    var onSaveProgress: ((positionSeconds: Int) -> Unit)? = null

    /** Fired when the Up next countdown reaches zero. */
    var onAutoAdvance: (() -> Unit)? = null

    /**
     * Bytes actually pulled over the network, so the data sheet's "This month" line
     * reports what was really spent rather than an estimate.
     *
     * Not reported while playing a download: that is served from disk, which is exactly
     * what "no data used" on the player promises.
     */
    var onBytesTransferred: ((Long) -> Unit)? = null

    init {
        adController.onStateChanged = { publish() }
        adController.onAdFinished = { publish() }

        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) captureMediaDuration()
                    if (playbackState == Player.STATE_ENDED) {
                        // An ad ending restores the content; the episode ending is the
                        // real end of playback.
                        if (!adController.onPlaybackEnded()) onEpisodeFinished?.invoke()
                    }
                    publish()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) saveProgress(force = true)
                    publish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A broken ad creative must never stop the episode.
                    if (!adController.onPlayerError()) {
                        failed = true
                        publish()
                    }
                }
            },
        )

        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onLoadCompleted(
                    eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData,
                ) {
                    if (fromDownload) return
                    val bytes = loadEventInfo.bytesLoaded
                    if (bytes > 0) onBytesTransferred?.invoke(bytes)
                }
            },
        )
    }

    /**
     * @param offline what the download holds, when this episode is downloaded. Non-null
     *   means playback is pinned to the cached renditions and needs no network at all.
     * @param schedule [AdSchedule.NONE] for a downloaded episode.
     */
    fun load(
        title: Title,
        episode: Episode,
        nextEpisode: Episode?,
        startPositionSeconds: Int,
        schedule: AdSchedule,
        rung: Rung,
        subtitles: SubtitleOption,
        offline: OfflinePlayback?,
    ) {
        this.title = title
        this.episode = episode
        this.nextEpisode = nextEpisode
        // Cleared because one controller serves both entry points and is reused across
        // loads: a stale label would caption a catalogue episode as a free source.
        this.sourceLabel = null
        this.attribution = null
        this.fromDownload = offline != null
        this.startPositionSeconds = startPositionSeconds
        this.upNextSeconds = null
        this.upNextElapsedMillis = 0L
        this.upNextSuppressed = false
        this.failed = false
        this.lastSavedPositionSeconds = -1

        val contentItem: MediaItem = offline
            ?.let { MediaItemFactory.forDownload(it, title, episode.number) }
            ?: MediaItemFactory.forContent(title, episode.number)

        adController.attach(
            player = player,
            schedule = schedule,
            contentItem = contentItem,
            contentDurationSeconds = episode.durationSeconds,
            startPositionSeconds = startPositionSeconds,
        )

        QualityController.applyRung(player, rung)
        QualityController.applySubtitles(player, subtitles)

        val startMs = startPositionSeconds * MILLIS
        // A pre-roll plays first and then hands over at the resume point (PRD §6.3).
        if (!adController.startPreRollIfAny(startMs)) {
            player.setMediaItem(contentItem)
            player.prepare()
            if (startMs > 0) player.seekTo(startMs)
            player.play()
        }
        publish()
    }

    /**
     * Plays a source resolved by the free-source layer, in this player rather than the
     * trailer's.
     *
     * Everything a catalogue episode brings is genuinely absent here, and is left absent
     * rather than faked:
     *
     * - **No ads.** An empty [AdSchedule] is still attached, because [AdController] holds
     *   the previous load's spots and `played` set; attaching resets them, where skipping
     *   the call would let a catalogue episode's mid-rolls fire over a public-domain film.
     * - **No episode**, so [effectiveDurationSeconds] falls through to the duration the
     *   player reports once it has read the container. Nothing has to be quoted up front.
     * - **No Up next and no next episode**, which the UI already hides on null.
     * - **No progress row** — see [PlayerArgs.Source].
     */
    fun loadSource(
        source: PlayableSource,
        startPositionSeconds: Int,
        rung: Rung,
        subtitles: SubtitleOption,
    ) {
        this.title = null
        this.episode = null
        this.nextEpisode = null
        this.sourceLabel = source.label
        this.attribution = source.attribution
        this.fromDownload = false
        this.startPositionSeconds = startPositionSeconds
        this.upNextSeconds = null
        this.upNextElapsedMillis = 0L
        this.upNextSuppressed = false
        this.failed = false
        this.lastSavedPositionSeconds = -1
        this.mediaDurationSeconds = 0

        val contentItem = MediaItemFactory.forPlayableSource(source)

        adController.attach(
            player = player,
            schedule = AdSchedule(emptyList()),
            contentItem = contentItem,
            // Unknown until the container is read; `updateContentDuration` re-bases it.
            contentDurationSeconds = 0,
            startPositionSeconds = startPositionSeconds,
        )

        QualityController.applyRung(player, rung)
        QualityController.applySubtitles(player, subtitles)

        player.setMediaItem(contentItem)
        player.prepare()
        if (startPositionSeconds > 0) player.seekTo(startPositionSeconds * MILLIS)
        player.play()
        publish()
    }

    /**
     * The clock: ad, then Up next, then position — the design's own resolution order.
     *
     * @param deltaMillis time since the previous tick. Taking it as a parameter keeps the
     *   Up next countdown correct at any tick rate: the rail wants ~4 Hz to look smooth,
     *   while the countdown is specified in whole seconds.
     */
    fun tick(deltaMillis: Long) {
        adController.onTick()
        if (adController.inAd) {
            publish()
            return
        }
        advanceUpNext(deltaMillis)
        saveProgress(force = false)
        publish()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        publish()
    }

    fun seekTo(positionSeconds: Int) {
        if (adController.inAd) return
        // Clamped against the media actually loaded, like every other playback mechanic in
        // this class. Reading `episode?.durationSeconds` directly is what broke seeking on
        // the whole PlayableSource path: a free source has no episode, so the upper bound
        // was 0 and `coerceIn(0, 0)` sent every seek — rail, skip buttons, swipe — back to
        // the start. A catalogue episode has one, which is the only reason this survived.
        val clamped = clampSeek(positionSeconds, effectiveDurationSeconds)
        // Seeking cancels Up next, and un-suppresses it, exactly as the design's
        // `seek: (s) => ({ ...p, pos: s, upNext: null, noNext: false })` does.
        upNextSeconds = null
        upNextSuppressed = false
        if (!adController.interceptSeek(clamped)) {
            player.seekTo(clamped * MILLIS)
        }
        publish()
    }

    fun skipBy(deltaSeconds: Int) {
        if (adController.inAd) return
        seekTo(positionSeconds() + deltaSeconds)
    }

    fun skipAd() {
        adController.skip()
        publish()
    }

    fun applyRung(rung: Rung) {
        QualityController.applyRung(player, rung)
    }

    fun applySubtitles(option: SubtitleOption) {
        QualityController.applySubtitles(player, option)
    }

    /** Shows the Up next card immediately — the player's "Next" chip. */
    fun startUpNext() {
        if (nextEpisode == null || adController.inAd) return
        upNextSuppressed = false
        showUpNext()
        publish()
    }

    /** Cancel suppresses the card for this episode only (FR-206). */
    fun cancelUpNext() {
        upNextSeconds = null
        upNextSuppressed = true
        analytics.upNextCancel()
        publish()
    }

    fun positionSeconds(): Int = (player.currentPosition / MILLIS).toInt().coerceAtLeast(0)

    /** Writes the current position regardless of the 10 s interval — back, pause, onStop. */
    fun flushProgress() = saveProgress(force = true)

    fun release() {
        saveProgress(force = true)
        adController.release()
        player.release()
    }

    // ---------------------------------------------------------------- internals

    /**
     * Records the media's real duration once it is known, and re-bases the ad breaks on it
     * so the rail's ticks and the breaks that fire stay the same positions.
     */
    private fun captureMediaDuration() {
        if (adController.inAd) return
        val reported = player.duration
        if (reported == C.TIME_UNSET || reported <= 0) return
        val seconds = (reported / MILLIS).toInt()
        if (seconds == mediaDurationSeconds) return
        mediaDurationSeconds = seconds
        adController.updateContentDuration(seconds, positionSeconds())
    }

    private fun advanceUpNext(deltaMillis: Long) {
        val duration = effectiveDurationSeconds.takeIf { it > 0 } ?: return
        val position = positionSeconds()

        if (upNextSeconds != null) {
            // Accumulated rather than decremented per tick, so the countdown is 8 real
            // seconds whatever the tick rate. It only advances while playing, because the
            // caller only ticks while playing.
            upNextElapsedMillis += deltaMillis
            val remaining = UP_NEXT_SECONDS - (upNextElapsedMillis / MILLIS).toInt()
            if (remaining <= 0) {
                upNextSeconds = null
                onAutoAdvance?.invoke()
            } else {
                upNextSeconds = remaining
            }
            return
        }

        // FR-206: appears at end minus 20 s, only when there is a successor and the viewer
        // has not cancelled it for this episode.
        val lead = duration - UP_NEXT_LEAD_SECONDS
        if (nextEpisode != null && !upNextSuppressed && duration > 0 && position >= lead) {
            showUpNext()
        }
    }

    private fun showUpNext() {
        upNextElapsedMillis = 0L
        upNextSeconds = UP_NEXT_SECONDS
        analytics.upNextShown()
    }

    private fun saveProgress(force: Boolean) {
        if (adController.inAd) return
        val position = positionSeconds()
        val duration = effectiveDurationSeconds
        if (duration <= 0) return
        val due = force ||
            lastSavedPositionSeconds < 0 ||
            position - lastSavedPositionSeconds >= Progress.SAVE_INTERVAL_SECONDS ||
            position < lastSavedPositionSeconds
        if (!due) return
        lastSavedPositionSeconds = position
        onSaveProgress?.invoke(position)
    }

    private fun publish() {
        val duration = effectiveDurationSeconds
        val position = if (adController.inAd) {
            // While an ad plays the rail must keep showing the content position, not the
            // creative's.
            state.positionSeconds
        } else {
            positionSeconds().coerceAtMost(duration)
        }

        state = PlaybackState(
            title = title,
            episode = episode,
            nextEpisode = nextEpisode,
            sourceLabel = sourceLabel,
            attribution = attribution,
            positionSeconds = position,
            durationSeconds = duration,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            ad = adController.state,
            adBreakFractions = adController.midRollFractions,
            playingFromDownload = fromDownload,
            upNextSeconds = upNextSeconds,
            failed = failed,
        )
        onStateChanged?.invoke(state)
    }

    /** Has the episode been watched through (PRD §6.3)? */
    fun isWatchedThrough(): Boolean {
        val duration = effectiveDurationSeconds
        return duration > 0 && isFinished(positionSeconds(), duration)
    }

    companion object {
        /** FR-206. */
        const val UP_NEXT_SECONDS = 8
        const val UP_NEXT_LEAD_SECONDS = 20

        private const val MILLIS = 1_000L
    }
}

/**
 * Where a requested seek actually lands.
 *
 * A top-level function rather than a line inside [PlaybackController.seekTo] because the
 * controller owns an ExoPlayer and cannot be constructed off-device, and this rule shipped
 * wrong: clamping to a duration of zero silently rewrote every seek to the start of the
 * media, which presents as "the scrubber does nothing".
 *
 * An unknown duration therefore imposes **no** upper bound. ExoPlayer clamps to the real
 * duration itself once it has read the container, and a seek that overshoots by a few
 * seconds is a far better failure than one that always lands on zero.
 */
internal fun clampSeek(requestedSeconds: Int, durationSeconds: Int): Int =
    if (durationSeconds > 0) {
        requestedSeconds.coerceIn(0, durationSeconds)
    } else {
        requestedSeconds.coerceAtLeast(0)
    }
