package com.example.streamingappzb.ui.base

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import android.content.res.ColorStateList
import kotlin.math.roundToInt

/** `dp` to pixels. */
fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.dp(value: Int): Int = dp(value.toFloat())

fun View.dp(value: Int): Int = context.dp(value)

fun Context.dimen(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)

fun View.dimen(@DimenRes id: Int): Int = context.dimen(id)

@ColorInt
fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

@ColorInt
fun View.color(@ColorRes id: Int): Int = context.color(id)

@ColorInt
fun Fragment.color(@ColorRes id: Int): Int = requireContext().color(id)

fun Fragment.dimen(@DimenRes id: Int): Int = requireContext().dimen(id)

val ViewGroup.inflater: LayoutInflater get() = LayoutInflater.from(context)

fun ImageView.tint(@ColorInt colour: Int) {
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(colour))
}

fun ImageView.tintRes(@ColorRes id: Int) = tint(color(id))

var View.isVisibleOrGone: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

/**
 * Edge-to-edge insets (PRD §8): an app bar pads by the status-bar inset, a bottom nav by
 * the navigation-bar inset. Padding is applied on top of whatever the layout already has,
 * and the listener is idempotent so a re-dispatch cannot compound it.
 */
fun View.padTopForStatusBar(extraPx: Int = 0) {
    val basePaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = basePaddingTop + top + extraPx)
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.padBottomForNavigationBar(extraPx: Int = 0) {
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = basePaddingBottom + bottom + extraPx)
        insets
    }
    requestApplyInsetsWhenAttached()
}

/** Both edges at once, for a full-bleed scroller that must not run under the bars. */
fun View.padForSystemBars() {
    val top = paddingTop
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = top + bars.top, bottom = bottom + bars.bottom)
        insets
    }
    requestApplyInsetsWhenAttached()
}

private fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
    }
}

/**
 * Guards against a double tap opening two screens. Anything that navigates or enqueues
 * work uses this rather than [View.setOnClickListener].
 */
fun View.onSingleClick(windowMs: Long = 500L, action: (View) -> Unit) {
    setOnClickListener(
        object : View.OnClickListener {
            private var last = 0L

            override fun onClick(v: View) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - last < windowMs) return
                last = now
                action(v)
            }
        },
    )
}
