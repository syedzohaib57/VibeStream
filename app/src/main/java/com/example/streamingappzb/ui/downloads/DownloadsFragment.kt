package com.example.streamingappzb.ui.downloads

import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentDownloadsBinding
import com.example.streamingappzb.databinding.ItemDownloadBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.DownloadRow
import com.example.streamingappzb.domain.model.DownloadsView
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.main.MainActivity
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.widget.MhTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Downloads (PRD §6.5).
 *
 * Rows are added to two plain containers rather than a RecyclerView: a viewer's offline
 * library is a handful of episodes, the two sections have different headings, and the
 * whole screen scrolls as one with the settings strip and the storage bar above it.
 */
class DownloadsFragment : BaseFragment<FragmentDownloadsBinding>() {

    private val viewModel: DownloadsViewModel by viewModel()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentDownloadsBinding =
        FragmentDownloadsBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()
        binding.btnFindSomething.onSingleClick {
            (activity as? MainActivity)?.selectTab(MhTab.Home)
        }
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest(::render)
        }
        lifecycleScope.launch {
            viewModel.completed
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { playCompletedAnimation() }
        }
    }

    /** The cyan tick, once, when something finishes downloading while this is on screen. */
    private fun playCompletedAnimation() {
        val view = bindingOrNull?.completedAnimation ?: return
        view.isVisible = true
        view.alpha = 1f
        view.progress = 0f
        view.playAnimation()
        view.animate()
            .alpha(0f)
            .setStartDelay(COMPLETED_HOLD_MS)
            .setDuration(COMPLETED_FADE_MS)
            .withEndAction { bindingOrNull?.completedAnimation?.isVisible = false }
            .start()
    }

    private fun render(state: DownloadsView) {
        renderSettings(state)
        renderStorage(state)

        binding.emptyState.isVisible = state.isEmpty

        binding.sectionInProgress.isVisible = state.inProgress.isNotEmpty()
        binding.sectionReady.isVisible = state.ready.isNotEmpty()
        fill(binding.inProgressRows, state.inProgress)
        fill(binding.readyRows, state.ready)
    }

    private fun renderSettings(state: DownloadsView) {
        binding.rowWifiOnly.apply {
            setIcon(R.drawable.ic_wifi)
            setLabel(R.string.download_on_wifi_only)
            setSub(
                getString(
                    if (state.network.isMetered) {
                        R.string.download_on_wifi_only_sub_cell
                    } else {
                        R.string.download_on_wifi_only_sub_wifi
                    },
                ),
            )
            withToggle(state.wifiOnly) { viewModel.setWifiOnly(it) }
        }

        binding.rowSmart.apply {
            setIcon(R.drawable.ic_auto_mode)
            setLabel(R.string.smart_downloads)
            setSub(R.string.smart_downloads_sub)
            withToggle(state.smartDownloads) { on -> showToast(viewModel.setSmartDownloads(on)) }
        }
    }

    /**
     * Real device figures rather than the design's placeholders: what this app holds, what
     * else is on the device, and what is left.
     */
    private fun renderStorage(state: DownloadsView) {
        val appMegabytes = state.usedMegabytes
        val stat = StatFs(requireContext().filesDir.absolutePath)
        val freeMegabytes = stat.availableBytes.toDouble() / BYTES_PER_MB
        val totalMegabytes = stat.totalBytes.toDouble() / BYTES_PER_MB
        val otherMegabytes = (totalMegabytes - freeMegabytes - appMegabytes).coerceAtLeast(0.0)

        binding.storageUsed.text = buildString {
            append(getString(R.string.storage_app_used, Format.megabytes(appMegabytes)))
            append(" · ")
            append(getString(R.string.storage_other, Format.megabytes(otherMegabytes)))
        }
        binding.storageFree.text = getString(R.string.storage_free, Format.megabytes(freeMegabytes))

        // Weights rather than widths, so the bar is correct at any screen size. A floor on
        // the app's share keeps a few megabytes from vanishing entirely.
        weight(binding.storageApp, appMegabytes.coerceAtLeast(totalMegabytes * MIN_VISIBLE_SHARE))
        weight(binding.storageOther, otherMegabytes)
        weight(binding.storageRemaining, freeMegabytes)
    }

    private fun weight(view: android.view.View, value: Double) {
        (view.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.weight = value.toFloat().coerceAtLeast(0f)
            view.layoutParams = params
        }
    }

    private fun fill(container: LinearLayout, rows: List<DownloadRow>) {
        container.removeAllViews()
        for (row in rows) {
            container.addView(bindRow(container, row))
        }
    }

    private fun bindRow(parent: ViewGroup, row: DownloadRow): android.view.View {
        val binding = ItemDownloadBinding.inflate(layoutInflater, parent, false)
        val item = row.item
        val done = item.state == DlState.Done

        binding.thumb.bind(row.title, showLabel = false)
        binding.playGlyph.isVisible = done
        binding.title.text = row.title.title

        binding.subtitle.text = if (row.title.isFilm) {
            item.rungId
        } else {
            getString(R.string.episode_and_rung, row.episode.number, row.episode.name, item.rungId)
        }

        // Every state is stated in words as well as colour (PRD §8).
        when {
            done -> {
                binding.status.isVisible = true
                binding.progressRow.isVisible = false
                val now = System.currentTimeMillis()
                val days = item.expiresInDays(now)
                val expiry = when {
                    days == null -> ""
                    // LicenceExpiryWorker clears these daily, but a row can be on screen
                    // when the window lapses.
                    item.isExpired(now) -> getString(R.string.expired)
                    days <= 0 -> getString(R.string.expires_today)
                    else -> resources.getQuantityString(R.plurals.expires_in_days_plural, days, days)
                }
                binding.status.text = getString(
                    R.string.size_and_expiry,
                    Format.megabytes(item.megabytes),
                    expiry,
                )
                binding.status.setTextColor(
                    color(
                        if (days != null && days < DownloadItem.EXPIRY_WARNING_DAYS) {
                            R.color.rose400
                        } else {
                            R.color.text_mid
                        },
                    ),
                )
            }

            item.state == DlState.Waiting -> {
                binding.status.isVisible = true
                binding.progressRow.isVisible = false
                binding.status.setText(R.string.status_waiting_wifi)
                binding.status.setTextColor(color(R.color.cyan400))
            }

            else -> {
                binding.status.isVisible = false
                binding.progressRow.isVisible = true
                binding.progress.progress = item.percent
                binding.progressLabel.text = when (item.state) {
                    DlState.Paused -> getString(R.string.status_paused)
                    DlState.Queued -> getString(R.string.status_queued)
                    DlState.Failed -> getString(R.string.action_retry)
                    else -> getString(R.string.status_percent, item.percent)
                }
            }
        }

        // Done or parked: delete. Running or paused: pause/resume (PRD §6.5).
        val deletes = done || item.state == DlState.Waiting
        binding.btnAction.setImageResource(
            when {
                deletes -> R.drawable.ic_delete
                item.state == DlState.Paused -> R.drawable.ic_play_arrow
                else -> R.drawable.ic_pause
            },
        )
        binding.btnAction.contentDescription = getString(
            when {
                deletes -> R.string.cd_download_delete
                item.state == DlState.Paused -> R.string.cd_download_resume
                else -> R.string.cd_download_pause
            },
        )
        binding.btnAction.onSingleClick {
            if (deletes) {
                viewModel.remove(item.key)
                showToast(R.string.toast_download_removed)
            } else {
                viewModel.togglePause(item.key)
            }
        }

        if (done) {
            // Plays from the cache, so this works with no network at all.
            binding.root.onSingleClick {
                Navigator.play(requireContext(), item.titleId, item.episode, 0)
            }
        }
        return binding.root
    }

    private companion object {
        const val BYTES_PER_MB = 1_000_000.0

        /** Keeps a small library visible on the bar instead of rounding to nothing. */
        const val MIN_VISIBLE_SHARE = 0.005

        const val COMPLETED_HOLD_MS = 900L
        const val COMPLETED_FADE_MS = 300L
    }
}
