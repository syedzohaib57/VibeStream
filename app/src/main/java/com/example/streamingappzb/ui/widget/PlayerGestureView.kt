package com.example.streamingappzb.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The player's touch surface: tap to reveal the chrome, double-tap the edges to skip,
 * drag sideways to scrub, drag vertically for brightness and volume.
 *
 * All of it lives in one view rather than on the `PlayerView` because the gestures have to
 * be decided against each other — a vertical drag must not also scrub, and a double-tap
 * must not also toggle the chrome. A [GestureDetector] resolves the taps; the drags are
 * tracked here because the detector has no concept of an axis-locked gesture.
 *
 * The view is deliberately inert while an ad plays — see [gesturesEnabled] — so the viewer
 * cannot seek past a break with a swipe.
 */
class PlayerGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        /** Toggle the chrome. Fires only once the double-tap window has passed. */
        fun onSingleTap()

        fun onDoubleTapSkip(forward: Boolean)

        fun onScrubStart()

        /** @param seconds absolute target, already clamped to the media. */
        fun onScrub(seconds: Int)

        fun onScrubEnd(seconds: Int)

        /** @param delta fraction of the screen height dragged; up is positive. */
        fun onBrightnessDelta(delta: Float)

        fun onVolumeDelta(delta: Float)

        /** Any vertical gesture finished, so its HUD can fade. */
        fun onVerticalEnd()
    }

    var listener: Listener? = null

    /** Ads make every gesture inert; see the class doc. */
    var gesturesEnabled: Boolean = true

    /** Needed to map a horizontal drag onto a seek target. */
    var durationSeconds: Int = 0

    /** Where a scrub starts from. */
    var positionSeconds: Int = 0

    private enum class Mode { None, Scrub, Brightness, Volume }

    private var mode = Mode.None
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var scrubTarget = 0
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val taps = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            // Confirmed, not raw: a raw single tap would also fire on the first of a
            // double-tap and flash the chrome before the skip registered.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (gesturesEnabled) listener?.onSingleTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!gesturesEnabled) return false
                // The middle of the screen is neither edge: a double-tap there is the
                // viewer missing, not asking to skip, so it does nothing.
                val third = width / DEAD_ZONE_DIVISOR
                when {
                    e.x < third -> listener?.onDoubleTapSkip(forward = false)
                    e.x > width - third -> listener?.onDoubleTapSkip(forward = true)
                }
                return true
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // The detector sees every event, so taps keep working during and after a drag.
        taps.onTouchEvent(event)
        if (!gesturesEnabled) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                mode = Mode.None
                scrubTarget = positionSeconds
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.None) pickMode(event) else continueDrag(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    /**
     * Locks the gesture to one axis on first travel past the touch slop.
     *
     * Deciding once and sticking to it is what stops a scrub from drifting into a volume
     * change halfway through — the comparison is made on the whole movement so far, not
     * per-event.
     */
    private fun pickMode(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        if (abs(dx) < slop && abs(dy) < slop) return

        mode = when {
            abs(dx) > abs(dy) -> Mode.Scrub
            downX < width / 2f -> Mode.Brightness
            else -> Mode.Volume
        }
        if (mode == Mode.Scrub) listener?.onScrubStart()
        lastY = event.y
    }

    private fun continueDrag(event: MotionEvent) {
        when (mode) {
            Mode.Scrub -> {
                if (durationSeconds <= 0 || width <= 0) return
                val fraction = (event.x - downX) / width
                scrubTarget = (positionSeconds + (fraction * SCRUB_SECONDS_FULL_WIDTH).toInt())
                    .coerceIn(0, durationSeconds)
                listener?.onScrub(scrubTarget)
            }

            Mode.Brightness, Mode.Volume -> {
                if (height <= 0) return
                // Incremental, not absolute: the level being adjusted already has a value,
                // and snapping it to where the finger happens to be would jump it.
                val delta = (lastY - event.y) / height
                lastY = event.y
                if (mode == Mode.Brightness) {
                    listener?.onBrightnessDelta(delta)
                } else {
                    listener?.onVolumeDelta(delta)
                }
            }

            Mode.None -> Unit
        }
    }

    private fun endDrag() {
        when (mode) {
            Mode.Scrub -> listener?.onScrubEnd(scrubTarget)
            Mode.Brightness, Mode.Volume -> listener?.onVerticalEnd()
            Mode.None -> Unit
        }
        mode = Mode.None
    }

    private companion object {
        /**
         * A full-width drag moves two minutes, rather than the whole runtime.
         *
         * Proportional scrubbing on a 94-minute film puts 20 seconds inside a few pixels,
         * which is unusable for the thing people actually do with this gesture — nudging
         * past a slow stretch. Long jumps are what the seek rail is for.
         */
        const val SCRUB_SECONDS_FULL_WIDTH = 120

        /** Each edge third is live; the middle third ignores double-taps. */
        const val DEAD_ZONE_DIVISOR = 3
    }
}
