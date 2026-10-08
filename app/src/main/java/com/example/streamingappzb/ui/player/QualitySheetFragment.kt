package com.example.streamingappzb.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.SheetRowsBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.SubtitleOption
import com.example.streamingappzb.ui.base.BaseBottomSheet
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.title.DownloadSheetFragment
import com.example.streamingappzb.ui.widget.MhSheetRow

/**
 * The quality sheet (§6.7) — opened from the chip that always shows the current cost.
 *
 * Every rung states its cost per hour, and a rung above the Data Saver ceiling on mobile
 * data is **locked**: dimmed, with a lock glyph *and* the words "Turn off Data Saver to
 * use", because colour alone is never the signal (PRD §8).
 */
class QualitySheetFragment : BaseBottomSheet<SheetRowsBinding>() {

    interface Host {
        fun qualityRows(): List<com.example.streamingappzb.domain.usecase.QualityOption>

        fun isSaverOn(): Boolean

        fun isOnMeteredNetwork(): Boolean

        fun onToggleSaver()

        fun onRungPicked(rungId: String)
    }

    private val host: Host? get() = activity as? Host

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): SheetRowsBinding =
        SheetRowsBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.sheetTitle.setText(R.string.sheet_quality)
        binding.divider.isVisible = true
        render()
    }

    private fun render() {
        val current = host ?: run { dismissAllowingStateLoss(); return }

        binding.header.removeAllViews()
        binding.header.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_data_saver_on)
                setLabel(R.string.data_saver)
                setSub(
                    if (current.isOnMeteredNetwork()) {
                        getString(R.string.data_saver_sub_cell)
                    } else {
                        getString(R.string.data_saver_sub_wifi)
                    },
                )
                withToggle(current.isSaverOn()) {
                    current.onToggleSaver()
                    // Toggling Saver re-applies the FR-204 defaults, so every row's lock
                    // state and the selection both change.
                    render()
                }
            },
        )

        binding.rows.removeAllViews()
        for (option in current.qualityRows()) {
            val row = MhSheetRow(requireContext()).apply {
                setLabel(option.rung.id)
                setSub(DownloadSheetFragment.RUNG_SUBTITLES[option.rung.subKey]?.let(::getString))
                setTrailingText(
                    getString(R.string.per_hour_approx, Format.megabytes(option.rung.mbPerHour)),
                )
                if (option.locked) {
                    asLocked()
                    onSingleClick { showToast(R.string.toast_saver_locked) }
                } else {
                    asUnlocked()
                    withRadio(option.selected)
                    onSingleClick {
                        current.onRungPicked(option.rung.id)
                        dismiss()
                    }
                }
            }
            binding.rows.addView(row)
        }
    }

    companion object {
        const val TAG = "quality-sheet"
    }
}

/**
 * Subtitles (FR-207): English or Off, nothing else.
 */
class SubtitleSheetFragment : BaseBottomSheet<SheetRowsBinding>() {

    interface Host {
        fun currentSubtitles(): SubtitleOption

        fun onSubtitlesPicked(option: SubtitleOption)
    }

    private val host: Host? get() = activity as? Host

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): SheetRowsBinding =
        SheetRowsBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        val current = host ?: run { dismissAllowingStateLoss(); return }
        binding.sheetTitle.setText(R.string.sheet_subtitles)

        val selected = current.currentSubtitles()
        binding.rows.removeAllViews()
        for (option in listOf(SubtitleOption.Off, SubtitleOption.English)) {
            val row = MhSheetRow(requireContext()).apply {
                setLabel(
                    getString(
                        if (option == SubtitleOption.Off) {
                            R.string.subtitles_off
                        } else {
                            R.string.subtitles_english
                        },
                    ),
                )
                withRadio(option == selected)
                onSingleClick {
                    current.onSubtitlesPicked(option)
                    dismiss()
                }
            }
            binding.rows.addView(row)
        }
    }

    companion object {
        const val TAG = "subtitle-sheet"
    }
}
