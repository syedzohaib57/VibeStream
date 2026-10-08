package com.example.streamingappzb.ui.title

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.SheetDownloadBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.ui.base.BaseBottomSheet
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.widget.MhSheetRow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The download sheet (FR-301..303).
 *
 * Deliberately stateless: the host Activity owns the ViewModel and exposes the state and
 * the three actions through [Host], which keeps a parameterised ViewModel out of the
 * sheet's own lifecycle.
 *
 * Every rung row shows its size **before anything downloads** (FR-302). On mobile data
 * with Wi-Fi-only on, the primary button queues for Wi-Fi and the secondary offers to
 * spend data — that way nothing consumes a viewer's allowance by default.
 */
class DownloadSheetFragment : BaseBottomSheet<SheetDownloadBinding>() {

    interface Host {
        val downloadSheetState: StateFlow<DownloadSheetState?>

        fun onRungSelected(rungId: String)

        fun onConfirmDownload(useMobileDataNow: Boolean)

        fun onDownloadSheetDismissed()
    }

    private val host: Host? get() = activity as? Host

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): SheetDownloadBinding =
        SheetDownloadBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        val flow = host?.downloadSheetState ?: run { dismissAllowingStateLoss(); return }
        lifecycleScope.launch {
            flow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest { state ->
                if (state == null) dismissAllowingStateLoss() else render(state)
            }
        }
    }

    private fun render(state: DownloadSheetState) {
        binding.sheetTitle.text = when {
            state.episode == null -> getString(R.string.sheet_download_season)
            state.isFilm -> getString(R.string.sheet_download_film, state.titleName)
            else -> getString(R.string.sheet_download_episode, state.episode)
        }

        binding.rungs.removeAllViews()
        for (option in state.options) {
            val row = MhSheetRow(requireContext()).apply {
                setLabel(option.rung.id)
                setSub(rungSubtitle(option.rung.subKey))
                // FR-302: the size is on screen before the download exists.
                setTrailingText(Format.megabytes(option.megabytes))
                withRadio(option.selected)
                onSingleClick { host?.onRungSelected(option.rung.id) }
            }
            binding.rungs.addView(row)
        }

        if (state.queueForWifi) {
            binding.btnPrimary.setText(R.string.queue_for_wifi)
            binding.btnPrimary.setIconResource(R.drawable.ic_wifi)
            binding.btnSecondary.isVisible = true
            binding.note.isVisible = true
            binding.btnPrimary.onSingleClick { host?.onConfirmDownload(false) }
            binding.btnSecondary.onSingleClick { host?.onConfirmDownload(true) }
        } else {
            binding.btnPrimary.setText(R.string.action_download)
            binding.btnPrimary.setIconResource(R.drawable.ic_download)
            binding.btnSecondary.isVisible = false
            binding.note.isVisible = false
            binding.btnPrimary.onSingleClick { host?.onConfirmDownload(true) }
        }
    }

    /**
     * The rung's one-line explanation. The catalogue carries a strings.xml *name* rather
     * than the text itself, so a server-supplied ladder stays localisable.
     */
    private fun rungSubtitle(subKey: String): CharSequence {
        val res = RUNG_SUBTITLES[subKey] ?: return ""
        return getString(res)
    }

    override fun onDismiss(dialog: DialogInterface) {
        host?.onDownloadSheetDismissed()
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "download-sheet"

        /**
         * Mapping the catalogue's `subKey` to a resource, rather than resolving by name at
         * runtime, so R8 cannot strip an "unused" string.
         */
        val RUNG_SUBTITLES = mapOf(
            "rung_sub_144p" to R.string.rung_sub_144p,
            "rung_sub_240p" to R.string.rung_sub_240p,
            "rung_sub_360p" to R.string.rung_sub_360p,
            "rung_sub_480p" to R.string.rung_sub_480p,
            "rung_sub_720p" to R.string.rung_sub_720p,
            "rung_sub_1080p" to R.string.rung_sub_1080p,
        )
    }
}
