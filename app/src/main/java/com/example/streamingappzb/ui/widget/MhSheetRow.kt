package com.example.streamingappzb.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ViewSheetRowBinding
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen

/**
 * The design's `SheetRow`, shared by the data sheet, the quality sheet, the download sheet
 * and the Downloads settings strip.
 *
 * A dimmed row is a *locked* row — a rung above the Data Saver ceiling — so it also gets a
 * lock glyph and text, because colour and opacity are never the only signal (PRD §8).
 */
class MhSheetRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewSheetRowBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dimen(R.dimen.sheet_row_min_height)
        val h = context.dimen(R.dimen.sheet_padding_h)
        val v = context.dimen(R.dimen.gap_button_tight)
        setPadding(h, v, h, v)
    }

    fun setIcon(@DrawableRes iconRes: Int?) {
        binding.icon.isVisible = iconRes != null
        iconRes?.let(binding.icon::setImageResource)
    }

    fun setLabel(text: CharSequence) {
        binding.label.text = text
    }

    fun setLabel(@StringRes textRes: Int) = binding.label.setText(textRes)

    fun setSub(text: CharSequence?) {
        binding.sub.isVisible = !text.isNullOrBlank()
        binding.sub.text = text
    }

    fun setSub(@StringRes textRes: Int) {
        binding.sub.isVisible = true
        binding.sub.setText(textRes)
    }

    /** The right-hand figure — a download size, or "≈ 155 MB/hr" in the quality sheet. */
    fun setTrailingText(text: CharSequence?) {
        binding.trailingText.isVisible = !text.isNullOrBlank()
        binding.trailingText.text = text
    }

    /** Replaces the trailing slot's contents with a toggle, radio or lock glyph. */
    fun setTrailingView(view: View?) {
        binding.trailingSlot.removeAllViews()
        binding.trailingSlot.isVisible = view != null
        view?.let(binding.trailingSlot::addView)
    }

    /** Convenience for the common "toggle on the right" row. */
    fun withToggle(checked: Boolean, onChange: (Boolean) -> Unit): MhToggle {
        val toggle = MhToggle(context).apply {
            setCheckedSilently(checked)
            setOnCheckedChangeListener { on ->
                announceChecked(on)
                onChange(on)
            }
            // The row is the accessibility node. Left visible to TalkBack the switch would
            // be a second, unlabelled stop announcing only "on" — with no clue what it
            // controls.
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            isClickable = false
        }
        setTrailingView(toggle)
        // The row is the target; the toggle just reflects state.
        setOnClickListener { toggle.performClick() }
        announceChecked(checked)
        return toggle
    }

    /**
     * Announces the row as a switch, so TalkBack reads "<label>, switch, on" and offers the
     * toggle action, rather than reading the label as inert text.
     */
    private fun announceChecked(checked: Boolean) {
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Switch::class.java.name
                    info.isCheckable = true
                    info.isChecked = checked
                }
            },
        )
    }

    fun withRadio(selected: Boolean): MhRadio {
        val radio = MhRadio(context).apply { isChecked = selected }
        setTrailingView(radio)
        return radio
    }

    /** A rung above the Data Saver cap: dimmed, with a lock and an explanation. */
    fun asLocked() {
        alpha = LOCKED_ALPHA
        setSub(R.string.locked_turn_off_saver)
        setTrailingView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_lock)
                layoutParams = LayoutParams(
                    context.dimen(R.dimen.icon_md),
                    context.dimen(R.dimen.icon_md),
                )
                imageTintList = android.content.res.ColorStateList.valueOf(
                    context.color(R.color.text_low),
                )
            },
        )
    }

    fun asUnlocked() {
        alpha = 1f
    }

    private companion object {
        /** The design's `dim` opacity. */
        const val LOCKED_ALPHA = 0.45f
    }
}
