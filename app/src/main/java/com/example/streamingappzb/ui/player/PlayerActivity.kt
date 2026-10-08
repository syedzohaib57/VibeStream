package com.example.streamingappzb.ui.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.data.download.SmartDownloadWorker
import com.example.streamingappzb.data.player.PlaybackState
import com.example.streamingappzb.databinding.ActivityPlayerBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.AdPlaybackState
import com.example.streamingappzb.domain.model.PlayableSource
import com.example.streamingappzb.domain.model.StreamType
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.domain.usecase.QualityOption
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padForSystemBars
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.widget.PlayerGestureView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * The player (PRD §6.3) — the most important screen in the product.
 *
 * Every control is ours (`use_controller="false"`), because the specified behaviour is too
 * particular for a stock controller: skip unlocking at exactly 5 s of ad playback, ad
 * ticks aligned to the authored breaks, an 8-second Up next countdown, and a data note
 * that states what the current stream costs.
 */
@OptIn(UnstableApi::class)
class PlayerActivity :
    BaseActivity<ActivityPlayerBinding>(),
    QualitySheetFragment.Host,
    SubtitleSheetFragment.Host {

    private val args: PlayerArgs by lazy { resolveArgs() }

    private val viewModel: PlayerViewModel by viewModel { parametersOf(args) }

    private val audio: AudioManager by lazy { getSystemService(AudioManager::class.java) }

    /** Carries the sub-step part of a volume drag between events; see [adjustVolume]. */
    private var volumeRemainder = 0f

    override fun inflateBinding(inflater: LayoutInflater): ActivityPlayerBinding =
        ActivityPlayerBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        if (!args.isValid) {
            finish()
            return
        }

        binding.chrome.padForSystemBars()
        binding.playerView.player = viewModel.controller.player
        // Never let the screen sleep mid-episode.
        binding.playerView.keepScreenOn = true
        styleSubtitles()

        applyFullscreen()
        wireControls()
        wireGestures()
        renderFullscreenButton()
        observe()

        // Starts visible so the controls are discoverable, then gets out of the way.
        showChromeTemporarily()

        // Back saves progress *before* the screen pops (PRD §4).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                    finish()
                }
            },
        )
    }

    /**
     * Subtitles as the design draws them: a 15sp medium-weight chip on 62% black with no
     * outline or shadow. `setApplyEmbeddedStyles(false)` is what stops a stream's own
     * baked-in caption styling from overriding it.
     */
    private fun styleSubtitles() {
        binding.playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setStyle(
                CaptionStyleCompat(
                    color(R.color.text_hi),
                    color(R.color.subtitle_bg),
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_NONE,
                    Color.TRANSPARENT,
                    ResourcesCompat.getFont(this@PlayerActivity, R.font.inter_medium),
                ),
            )
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, SUBTITLE_TEXT_SP)
        }
    }

    // ---------------------------------------------------------------- layout

    /**
     * Immersive in both orientations.
     *
     * Portrait used to letterbox a 16:9 frame into the top of the page and stack the
     * controls underneath it. The frame is now full-bleed and the chrome floats over it, so
     * pinning the frame to 16:9 would leave the controls hanging over empty black —
     * `PlayerView`'s `resize_mode="fit"` already letterboxes the *video* inside the frame,
     * which is what that sizing was really for.
     */
    private fun applyFullscreen() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * Rotates the player itself, instead of waiting for the device to be turned.
     *
     * Turning the phone was the only way into landscape, which does nothing at all when the
     * system's auto-rotate is off — so for a lot of viewers there was no fullscreen.
     *
     * Setting `requestedOrientation` is safe here specifically because the manifest
     * declares `configChanges="orientation|screenSize|…"`: the Activity is reconfigured
     * rather than recreated, so the ExoPlayer instance, its buffer and the playback
     * position all survive the rotation. Without that flag this would restart the stream.
     */
    private fun toggleFullscreen() {
        requestedOrientation = if (isLandscape()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            // Sensor, not plain LANDSCAPE, so the picture follows which way it is held.
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        showChromeTemporarily()
    }

    /**
     * The button's icon, set in code rather than per layout.
     *
     * `configChanges` means the layout is never re-inflated, so the view that is on screen
     * after a rotation is whichever variant was inflated at creation — its XML `src` can
     * therefore be wrong for the current orientation.
     */
    private fun renderFullscreenButton() {
        val landscape = isLandscape()
        binding.btnFullscreen.setImageResource(
            if (landscape) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
        )
        binding.btnFullscreen.contentDescription =
            getString(if (landscape) R.string.cd_exit_fullscreen else R.string.cd_fullscreen)
    }

    // ---------------------------------------------------------------- controls

    private fun wireControls() {
        binding.btnClose.onSingleClick {
            viewModel.onBackPressed()
            finish()
        }
        // Each of these resets the hide timer, so using the controls never has them fade
        // out from under the viewer's finger.
        binding.btnPlayPause.onSingleClick {
            viewModel.togglePlayPause()
            showChromeTemporarily()
        }
        binding.btnReplay10.onSingleClick {
            viewModel.replay10()
            showChromeTemporarily()
        }
        binding.btnForward10.onSingleClick {
            viewModel.forward10()
            showChromeTemporarily()
        }
        binding.btnSkipAd.onSingleClick { viewModel.skipAd() }
        binding.btnNext.onSingleClick { viewModel.showUpNext() }
        binding.btnUpNextCancel.onSingleClick { viewModel.cancelUpNext() }
        binding.btnUpNextPlay.onSingleClick { viewModel.playNext() }

        binding.btnQuality.onSingleClick {
            QualitySheetFragment().show(supportFragmentManager, QualitySheetFragment.TAG)
        }
        binding.btnSubtitles.onSingleClick {
            SubtitleSheetFragment().show(supportFragmentManager, SubtitleSheetFragment.TAG)
        }

        binding.btnFullscreen.onSingleClick { toggleFullscreen() }

        // Chromecast discovery is out of scope for v1 (PRD §1), so this states as much.
        binding.btnCast.onSingleClick { showToast(R.string.toast_cast) }
        binding.btnPip.onSingleClick { enterPip() }

        binding.seekRail.onScrub = { seconds ->
            binding.position.text = Format.time(seconds)
            // Dragging the rail must not have the chrome vanish mid-gesture.
            cancelChromeHide()
        }
        binding.seekRail.onSeek = { seconds ->
            viewModel.seekTo(seconds)
            showChromeTemporarily()
        }
    }

    // ---------------------------------------------------------------- gestures

    private fun wireGestures() {
        binding.gestures.listener = object : PlayerGestureView.Listener {
            override fun onSingleTap() = toggleChrome()

            override fun onDoubleTapSkip(forward: Boolean) {
                if (forward) viewModel.forward10() else viewModel.replay10()
                flashSkip(forward)
            }

            override fun onScrubStart() {
                cancelChromeHide()
                setChromeVisible(true)
                binding.scrubHud.isVisible = true
            }

            override fun onScrub(seconds: Int) {
                binding.scrubHud.text = scrubLabel(seconds)
                binding.position.text = Format.time(seconds)
                binding.seekRail.positionSeconds = seconds
            }

            override fun onScrubEnd(seconds: Int) {
                binding.scrubHud.isVisible = false
                viewModel.seekTo(seconds)
                showChromeTemporarily()
            }

            override fun onBrightnessDelta(delta: Float) = adjustBrightness(delta)

            override fun onVolumeDelta(delta: Float) = adjustVolume(delta)

            override fun onVerticalEnd() {
                binding.levelHud.animate()
                    .alpha(0f)
                    .setStartDelay(HUD_LINGER_MILLIS)
                    .setDuration(CHROME_FADE_MILLIS)
                    .withEndAction { binding.levelHud.isVisible = false }
                    .start()
            }
        }
    }

    /** "24:10  +0:38" — the target, and how far the drag has moved from here. */
    private fun scrubLabel(target: Int): String {
        val delta = target - viewModel.state.value.playback.positionSeconds
        val signed = (if (delta < 0) "-" else "+") + Format.time(abs(delta))
        return getString(R.string.scrub_hud, Format.time(target), signed)
    }

    private fun flashSkip(forward: Boolean) {
        val hud = if (forward) binding.skipRightHud else binding.skipLeftHud
        hud.text = getString(R.string.skip_hud_seconds, SKIP_SECONDS)
        hud.animate().cancel()
        hud.alpha = 1f
        hud.isVisible = true
        hud.animate()
            .alpha(0f)
            .setStartDelay(SKIP_HUD_MILLIS)
            .setDuration(CHROME_FADE_MILLIS)
            .withEndAction { hud.isVisible = false }
            .start()
    }

    /**
     * Window-local brightness, so leaving the player restores whatever the device had.
     *
     * `screenBrightness` starts at [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE]
     * (-1, "follow the system"), which cannot be added to — the first nudge has to start
     * from the system's actual level or the screen jumps to near-black.
     */
    private fun adjustBrightness(delta: Float) {
        val attrs = window.attributes
        val current = attrs.screenBrightness.takeIf { it >= 0f } ?: systemBrightness()
        val next = (current + delta).coerceIn(MIN_BRIGHTNESS, 1f)
        attrs.screenBrightness = next
        window.attributes = attrs
        showLevel(R.drawable.ic_brightness, next)
    }

    private fun systemBrightness(): Float = runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) /
            SYSTEM_BRIGHTNESS_MAX
    }.getOrDefault(DEFAULT_BRIGHTNESS)

    /**
     * Volume moves in the stream's own steps, so the accumulator is not optional: a phone
     * with 15 steps needs ~7% of the screen per step, and rounding each small delta to zero
     * would make short drags do nothing at all.
     */
    private fun adjustVolume(delta: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return

        volumeRemainder += delta * max
        val steps = volumeRemainder.toInt()
        if (steps != 0) {
            volumeRemainder -= steps
            val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        }
        showLevel(R.drawable.ic_volume, audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat())
    }

    private fun showLevel(iconRes: Int, fraction: Float) {
        binding.levelIcon.setImageResource(iconRes)
        binding.levelBar.progress = (fraction * PERCENT).toInt()
        binding.levelHud.animate().cancel()
        binding.levelHud.alpha = 1f
        binding.levelHud.isVisible = true
    }

    // ---------------------------------------------------------------- chrome

    private val hideChrome = Runnable { setChromeVisible(false) }

    private fun toggleChrome() {
        if (binding.chrome.isVisible) setChromeVisible(false) else showChromeTemporarily()
    }

    private fun showChromeTemporarily() {
        setChromeVisible(true)
        cancelChromeHide()
        // Paused or mid-ad the controls stay put: there is nothing to look at behind them
        // while paused, and during a break Skip ad has to remain reachable.
        val playback = viewModel.state.value.playback
        if (playback.playing && !playback.inAd) {
            binding.root.postDelayed(hideChrome, CHROME_TIMEOUT_MILLIS)
        }
    }

    private fun cancelChromeHide() = binding.root.removeCallbacks(hideChrome)

    private fun setChromeVisible(visible: Boolean) {
        if (binding.chrome.isVisible == visible) return
        binding.chrome.animate().cancel()
        if (visible) {
            binding.chrome.alpha = 0f
            binding.chrome.isVisible = true
            binding.chrome.animate().alpha(1f).setDuration(CHROME_FADE_MILLIS).start()
        } else {
            binding.chrome.animate()
                .alpha(0f)
                .setDuration(CHROME_FADE_MILLIS)
                .withEndAction { binding.chrome.isVisible = false }
                .start()
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::render)
        }
        lifecycleScope.launch {
            viewModel.events
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::handle)
        }
    }

    private fun handle(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.Toast -> showToast(event.messageRes)

            is PlayerViewModel.PlayerEvent.ScheduleSmartDownload ->
                SmartDownloadWorker.enqueue(this, event.titleId, event.finishedEpisode)

            PlayerViewModel.PlayerEvent.Finish -> finish()

            PlayerViewModel.PlayerEvent.Fatal -> {
                showToast(R.string.error_playback_body)
                finish()
            }
        }
    }

    // ---------------------------------------------------------------- render

    private fun render(state: PlayerUiState) {
        val playback = state.playback
        val ad = playback.ad

        val title = playback.title
        if (title != null) {
            binding.playerTitle.text = title.title
            binding.playerSubtitle.text = playback.episode?.let { episode ->
                if (title.isFilm) {
                    getString(R.string.year_genre, title.year, title.genre)
                } else {
                    getString(R.string.episode_number_name, episode.number, episode.name)
                }
            }
        } else if (playback.sourceLabel != null) {
            // A free source has no catalogue row, so the subtitle carries its attribution.
            // That credit is a condition of using these files, not decoration.
            binding.playerTitle.text = playback.sourceLabel
            binding.playerSubtitle.text = playback.attribution
        }

        renderAd(ad)
        renderTransport(playback)
        renderUpNext(playback)

        // The quality chip is the PRD's central element: the rung and its cost per hour,
        // always on screen.
        binding.btnQuality.text = getString(
            R.string.quality_chip,
            state.rung.id,
            Format.megabytes(state.rung.mbPerHour),
        )
        binding.btnSubtitles.setText(
            if (state.subtitles == SubtitleOption.Off) {
                R.string.subtitles_off
            } else {
                R.string.subtitles_english
            },
        )

        binding.dataNote.setText(state.dataNoteRes)
        binding.dataNote.setTextColor(
            color(if (state.dataNoteIsCyan) R.color.cyan400 else R.color.text_low),
        )

        binding.btnNext.isVisible = playback.nextEpisode != null

        // The ember arc, only while content is actually buffering — an ad has its own slate.
        binding.buffering.isVisible = playback.buffering && !playback.inAd

        if (playback.failed) showToast(R.string.error_playback_body)
    }

    private fun renderAd(ad: AdPlaybackState) {
        binding.adBadge.isVisible = ad.inAd
        binding.adProgress.isVisible = ad.inAd
        binding.btnSkipAd.isVisible = ad.inAd
        binding.adSlate.isVisible = false

        if (!ad.inAd) {
            // Controls come back to full strength and become interactive again.
            setControlsDimmed(false)
            return
        }

        binding.adBadge.text = getString(R.string.ad_countdown, Format.time(ad.remainingSeconds))
        binding.adProgress.progress = Format.percent(ad.progressFraction)

        // "Skip in 5…1", disabled on 72% dark, then "Skip ad ›" in ember at exactly 5s.
        if (ad.canSkip) {
            binding.btnSkipAd.isEnabled = true
            binding.btnSkipAd.setText(R.string.skip_ad)
            binding.btnSkipAd.setBackgroundResource(0)
            binding.btnSkipAd.backgroundTintList =
                android.content.res.ColorStateList.valueOf(color(R.color.ember500))
            binding.btnSkipAd.setTextColor(color(R.color.text_on_ember))
            binding.btnSkipAd.contentDescription = getString(R.string.cd_skip_ad)
        } else {
            binding.btnSkipAd.isEnabled = false
            binding.btnSkipAd.text = getString(R.string.skip_in, ad.secondsUntilSkip)
            binding.btnSkipAd.backgroundTintList = null
            binding.btnSkipAd.setBackgroundResource(R.drawable.bg_skip_disabled)
            binding.btnSkipAd.setTextColor(color(R.color.text_mid))
        }

        setControlsDimmed(true)
    }

    /** While an ad plays, every other control is at 45% and does nothing (PRD §6.3). */
    private fun setControlsDimmed(dimmed: Boolean) {
        val alpha = if (dimmed) AdPlaybackState.DIMMED_ALPHA else 1f
        binding.controls.alpha = alpha
        binding.controls.isEnabled = !dimmed
        binding.seekRail.isEnabled = !dimmed
        binding.btnPlayPause.isEnabled = !dimmed
        binding.btnReplay10.isEnabled = !dimmed
        binding.btnForward10.isEnabled = !dimmed
        binding.btnNext.isEnabled = !dimmed
        binding.btnQuality.isEnabled = !dimmed
        binding.btnSubtitles.isEnabled = !dimmed
    }

    private fun renderTransport(playback: PlaybackState) {
        binding.seekRail.durationSeconds = playback.durationSeconds
        binding.seekRail.positionSeconds = playback.positionSeconds
        // The ticks and the breaks read the same list, so they cannot drift apart.
        binding.seekRail.adBreakFractions = playback.adBreakFractions

        binding.position.text = Format.time(playback.positionSeconds)
        binding.duration.text = Format.time(playback.durationSeconds)

        binding.btnPlayPause.setIconResource(
            if (playback.playing) R.drawable.ic_pause_fill else R.drawable.ic_play_arrow_fill,
        )
        binding.btnPlayPause.contentDescription =
            getString(if (playback.playing) R.string.cd_pause else R.string.cd_play)

        // The gesture surface needs the timeline to turn a drag into a seek target.
        binding.gestures.durationSeconds = playback.durationSeconds
        binding.gestures.positionSeconds = playback.positionSeconds
        // Inert during a break, so a swipe cannot seek past an ad.
        binding.gestures.gesturesEnabled = !playback.inAd

        // Paused is a state the viewer is in on purpose; the controls stay up for it.
        if (!playback.playing) {
            cancelChromeHide()
            setChromeVisible(true)
        }
    }

    private fun renderUpNext(playback: PlaybackState) {
        val seconds = playback.upNextSeconds
        val next = playback.nextEpisode
        val show = seconds != null && next != null && !playback.inAd
        binding.upNextCard.isVisible = show
        if (!show) return
        binding.upNextRing.total = com.example.streamingappzb.data.player.PlaybackController.UP_NEXT_SECONDS
        binding.upNextRing.remaining = seconds!!
        binding.upNextEpisode.text = getString(R.string.up_next_episode, next!!.number, next.name)
    }

    // ---------------------------------------------------------------- PiP

    /**
     * Real picture-in-picture rather than the toast PRD v1 allows for — nothing in the
     * brief asked for it to be deferred, and the player already owns its own surface.
     */
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showToast(R.string.toast_pip)
            return
        }
        runCatching { enterPictureInPictureMode(pipParams()) }
            .onFailure { showToast(R.string.toast_pip) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        // The rect the window animates out of, so the transition comes from the video
        // rather than from the whole screen.
        .setSourceRectHint(
            Rect().also { rect ->
                binding.playerView.getGlobalVisibleRect(rect)
            },
        )
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true)
        }
        .build()

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // In PiP the window is a thumbnail: only the video belongs in it.
        binding.chrome.isVisible = !isInPictureInPictureMode
        binding.vignette.isVisible = !isInPictureInPictureMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFullscreen()
        renderFullscreenButton()
        // The bars re-appear on rotation, so the controls come back with them rather than
        // leaving the viewer looking at a picture with no way to get back to portrait.
        showChromeTemporarily()
    }

    override fun onStop() {
        viewModel.onStopped()
        super.onStop()
    }

    // ---------------------------------------------------------------- sheet hosts

    override fun qualityRows(): List<QualityOption> = viewModel.qualityOptions()

    override fun isSaverOn(): Boolean = viewModel.state.value.saver

    override fun isOnMeteredNetwork(): Boolean = viewModel.state.value.network.isMetered

    override fun onToggleSaver() = viewModel.toggleSaver()

    override fun onRungPicked(rungId: String) = viewModel.setQuality(rungId)

    override fun currentSubtitles(): SubtitleOption = viewModel.state.value.subtitles

    override fun onSubtitlesPicked(option: SubtitleOption) = viewModel.setSubtitles(option)

    // ---------------------------------------------------------------- intent

    /**
     * Which of the two playback modes this launch is.
     *
     * A free source is checked first and comes only from an explicit Intent — never from
     * `Navigator.parse`, so no deep link can hand this player an arbitrary media URL.
     */
    private fun resolveArgs(): PlayerArgs {
        val position = intent?.getIntExtra(Navigator.EXTRA_POSITION_SECONDS, 0) ?: 0

        val sourceUrl = intent?.getStringExtra(Navigator.EXTRA_SOURCE_URL)
        if (!sourceUrl.isNullOrBlank()) {
            return PlayerArgs.Source(
                source = PlayableSource(
                    url = sourceUrl,
                    type = runCatching {
                        StreamType.valueOf(
                            intent?.getStringExtra(Navigator.EXTRA_SOURCE_TYPE).orEmpty(),
                        )
                    }.getOrDefault(StreamType.Progressive),
                    label = intent?.getStringExtra(Navigator.EXTRA_SOURCE_LABEL).orEmpty(),
                    attribution =
                        intent?.getStringExtra(Navigator.EXTRA_SOURCE_ATTRIBUTION).orEmpty(),
                ),
                positionSeconds = position,
            )
        }

        val id = intent?.getIntExtra(Navigator.EXTRA_TITLE_ID, -1) ?: -1
        if (id > 0) {
            return PlayerArgs.Catalog(
                titleId = id,
                episode = intent?.getIntExtra(Navigator.EXTRA_EPISODE, 1) ?: 1,
                positionSeconds = position,
            )
        }
        return when (val destination = Navigator.parse(intent?.data)) {
            is Navigator.Destination.Play -> PlayerArgs.Catalog(
                titleId = destination.titleId,
                episode = destination.episode,
                positionSeconds = destination.positionSeconds,
            )

            is Navigator.Destination.Title ->
                PlayerArgs.Catalog(titleId = destination.titleId, episode = 1)

            null -> PlayerArgs.Invalid
        }
    }

    private companion object {
        /** The design's subtitle chip size. */
        const val SUBTITLE_TEXT_SP = 15f

        /** Long enough to read the controls, short enough to stay out of the way. */
        const val CHROME_TIMEOUT_MILLIS = 3_500L
        const val CHROME_FADE_MILLIS = 180L

        /** How long a double-tap indicator sits before fading. */
        const val SKIP_HUD_MILLIS = 450L
        const val HUD_LINGER_MILLIS = 550L

        /** Matches the skip the transport buttons perform. */
        const val SKIP_SECONDS = 10

        /** Never all the way to black — the viewer would have no way to see the gesture. */
        const val MIN_BRIGHTNESS = 0.02f
        const val DEFAULT_BRIGHTNESS = 0.5f
        const val SYSTEM_BRIGHTNESS_MAX = 255f
        const val PERCENT = 100
    }
}
