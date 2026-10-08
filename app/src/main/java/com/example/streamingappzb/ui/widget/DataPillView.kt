package com.example.streamingappzb.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.dp
import com.example.streamingappzb.ui.base.tint

/**
 * The app bar's data pill (PRD §5 `DataPill`).
 *
 * The PRD's central design decision, and an acceptance item: it is **always visible on
 * Home** and reflects the network and Data Saver within a second of a change.
 *
 * The pill the design draws is 28dp tall, which is below the 44dp touch minimum, so the
 * view is 44dp and the pill background is inset — the target is full size while the
 * drawing matches.
 */
class DataPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val icon = ImageView(context).apply {
        layoutParams = LayoutParams(context.dimen(R.dimen.icon_sm), context.dimen(R.dimen.icon_sm))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val label = TextView(context).apply {
        setTextAppearance(R.style.TextAppearance_Mh_Caption_Semibold)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(5)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val pillHeight = context.dimen(R.dimen.data_pill_height)
    private val targetHeight = context.dimen(R.dimen.touch_min)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = targetHeight
        val h = context.dimen(R.dimen.data_pill_padding_h)
        setPadding(h, 0, h, 0)
        addView(icon)
        addView(label)
        render(NetworkState.Cellular, saver = true)
    }

    /**
     * @param network from NetworkMonitor
     * @param saver the Data Saver setting
     */
    fun render(network: NetworkState, saver: Boolean) {
        val metered = network.isMetered
        val saverOnData = metered && saver

        val glyph = when {
            network == NetworkState.Offline -> R.drawable.ic_wifi_off
            !metered -> R.drawable.ic_wifi
            saver -> R.drawable.ic_data_saver_on
            else -> R.drawable.ic_signal_cellular_alt
        }
        val textRes = when {
            network == NetworkState.Offline -> R.string.pill_offline
            !metered -> R.string.pill_wifi
            saver -> R.string.pill_saver
            else -> R.string.pill_mobile
        }

        // cyan = saving data or offline; plain white otherwise.
        val cyanState = saverOnData || network == NetworkState.Offline
        val fg = context.color(if (cyanState) R.color.cyan400 else R.color.text_hi)
        val bg = context.color(if (cyanState) R.color.data_pill_bg_saver else R.color.data_pill_bg)

        icon.setImageResource(glyph)
        icon.tint(fg)
        label.setText(textRes)
        label.setTextColor(fg)

        val inset = ((targetHeight - pillHeight) / 2).coerceAtLeast(0)
        background = InsetDrawable(
            ContextCompat.getDrawable(context, R.drawable.bg_pill)?.mutate()?.apply {
                setTintList(ColorStateList.valueOf(bg))
            },
            0, inset, 0, inset,
        )

        // The state is stated in words for TalkBack, not only in colour (PRD §8).
        contentDescription = context.getString(
            R.string.cd_data_pill,
            context.getString(textRes),
        )
    }
}
