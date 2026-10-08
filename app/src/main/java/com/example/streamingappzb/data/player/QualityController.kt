package com.example.streamingappzb.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.SubtitleOption

/**
 * Applies the chosen rung and subtitle track to a live ExoPlayer.
 *
 * The rung is applied with `setMaxVideoSize` plus `setMaxVideoBitrate` on the player's
 * existing track-selection parameters, which **does not restart the stream** (FR-204) —
 * ABR simply stops choosing anything above the ceiling from the next segment on. Nothing
 * already buffered is thrown away, which is the whole point on metered data.
 */
@OptIn(UnstableApi::class)
object QualityController {

    fun applyRung(player: ExoPlayer, rung: Rung) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(rung.maxWidth, rung.maxHeight)
            .setMaxVideoBitrate(rung.maxBitrateBps)
            .build()
    }

    /**
     * FR-207: English or Off, nothing else.
     *
     * Off disables the text renderer outright rather than merely preferring no language,
     * so a stream with forced or default subtitles cannot re-enable itself.
     */
    fun applySubtitles(player: ExoPlayer, option: SubtitleOption) {
        val builder: TrackSelectionParameters.Builder = player.trackSelectionParameters.buildUpon()
        player.trackSelectionParameters = when (option) {
            SubtitleOption.Off -> builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()

            SubtitleOption.English -> builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(option.tag)
                .build()
        }
    }
}
