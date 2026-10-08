package com.example.streamingappzb.ui.widget

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.dp

/**
 * The design's toast (PRD §5 `MhToast`): a centred pill on `night600`, shown for 2.6 s,
 * **replacing** any toast already on screen rather than queueing behind it.
 *
 * Not [android.widget.Toast]: that cannot be positioned above the bottom nav, cannot be
 * restyled, and on API 30+ is rate-limited and re-themed by the system.
 */
class MhToastHost(private val root: ViewGroup) {

    private var view: TextView? = null
    private val hide = Runnable { fadeOut() }

    fun show(message: CharSequence, bottomMarginPx: Int) {
        val target = view ?: create().also { view = it }
        target.removeCallbacks(hide)
        target.text = message
        target.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = if (bottomMarginPx > 0) {
                bottomMarginPx
            } else {
                root.context.dimen(R.dimen.toast_bottom_tab)
            }
        }
        target.bringToFront()
        target.animate().cancel()
        target.alpha = 0f
        target.visibility = View.VISIBLE
        target.animate().alpha(1f).setDuration(FADE_MS).start()
        target.postDelayed(hide, DURATION_MS)
    }

    fun dismiss() {
        view?.let {
            it.removeCallbacks(hide)
            it.animate().cancel()
            root.removeView(it)
        }
        view = null
    }

    private fun fadeOut() {
        val target = view ?: return
        target.animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .withEndAction { target.visibility = View.GONE }
            .start()
    }

    private fun create(): TextView {
        val ctx = root.context
        return TextView(ctx).apply {
            id = R.id.mh_toast
            maxWidth = ctx.dimen(R.dimen.toast_max_width)
            setBackgroundResource(R.drawable.bg_r12)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.night600))
            setTextColor(ctx.color(R.color.text_hi))
            setTextAppearance(R.style.TextAppearance_Mh_Meta)
            setTextColor(ctx.color(R.color.text_hi))
            gravity = Gravity.CENTER
            elevation = ctx.dp(8).toFloat()
            val h = ctx.dimen(R.dimen.toast_padding_h)
            val v = ctx.dimen(R.dimen.toast_padding_v)
            setPadding(h, v, h, v)
            visibility = View.GONE
            // Purely informational, so it must never swallow a tap meant for the screen.
            isClickable = false
            isFocusable = false
            root.addView(
                this,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                },
            )
        }
    }

    private companion object {
        /** PRD §5: 2.6 s. */
        const val DURATION_MS = 2_600L
        const val FADE_MS = 200L
    }
}
