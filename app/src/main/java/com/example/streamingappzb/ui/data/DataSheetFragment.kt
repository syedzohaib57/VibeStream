package com.example.streamingappzb.ui.data

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.analytics.Analytics
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.databinding.SheetRowsBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.repository.SettingsRepository
import com.example.streamingappzb.domain.usecase.ResolveQualityUseCase
import com.example.streamingappzb.ui.base.BaseBottomSheet
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.library.LibrarySheetFragment
import com.example.streamingappzb.ui.widget.MhSheetRow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The data sheet (§6.7), opened from the app bar's pill.
 *
 * Shows the current network read-only from NetworkMonitor, the Data Saver toggle (**on by
 * default**), Wi-Fi-only downloads, and a one-line estimate of what an hour of video
 * costs right now — the whole point of the product, stated plainly.
 */
class DataSheetFragment : BaseBottomSheet<SheetRowsBinding>() {

    private val session: SessionState by inject()
    private val settings: SettingsRepository by inject()
    private val analytics: Analytics by inject()

    /** Read directly: the folder list is a raw preference with no rules around it. */
    private val prefs: AppPrefs by inject()

    /** For the rung the FR-204 rules would pick right now, ignoring any manual override. */
    private val resolveQuality: ResolveQualityUseCase by inject()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): SheetRowsBinding =
        SheetRowsBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        analytics.dataSheetOpen()
        binding.sheetTitle.setText(R.string.sheet_data_use)
        binding.divider.isVisible = false

        lifecycleScope.launch {
            combine(
                session.networkState,
                session.saver,
                session.wifiOnly,
                session.quality,
            ) { network, saver, wifiOnly, rung ->
                Snapshot(network, saver, wifiOnly, rung)
            }.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect(::render)
        }
    }

    private data class Snapshot(
        val network: NetworkState,
        val saver: Boolean,
        val wifiOnly: Boolean,
        val rung: Rung,
    )

    private fun render(snapshot: Snapshot) {
        binding.rows.removeAllViews()

        // The network itself is read-only — it reports, it is not a setting.
        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(
                    when (snapshot.network) {
                        NetworkState.Wifi -> R.drawable.ic_wifi
                        NetworkState.Cellular -> R.drawable.ic_signal_cellular_alt
                        NetworkState.Offline -> R.drawable.ic_wifi_off
                    },
                )
                setLabel(
                    when (snapshot.network) {
                        NetworkState.Wifi -> R.string.on_wifi
                        NetworkState.Cellular -> R.string.on_mobile_data
                        NetworkState.Offline -> R.string.offline
                    },
                )
                // The figures come from the rules in force, so turning Data Saver off
                // updates them from 240p to 480p rather than leaving the sheet claiming a
                // cost the viewer is no longer paying.
                val default = resolveQuality.defaultFor(snapshot.network, snapshot.saver)
                setSub(
                    when (snapshot.network) {
                        NetworkState.Offline -> getString(R.string.offline_sub)
                        NetworkState.Wifi -> getString(
                            R.string.on_wifi_sub,
                            default.id,
                            Format.megabytes(default.mbPerHour),
                        )
                        NetworkState.Cellular -> getString(
                            R.string.on_mobile_data_sub,
                            default.id,
                            Format.megabytes(default.mbPerHour),
                        )
                    },
                )
            },
        )

        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_data_saver_on)
                setLabel(R.string.data_saver)
                setSub(R.string.data_saver_sheet_sub)
                withToggle(snapshot.saver) { on ->
                    settings.setSaver(on)
                    analytics.saverToggle(on)
                }
            },
        )

        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_wifi)
                setLabel(R.string.download_on_wifi_only)
                withToggle(snapshot.wifiOnly) { settings.setWifiOnly(it) }
            },
        )

        // The way in to the viewer's own media. It lives here rather than behind its own
        // app-bar affordance because this sheet is already the app's settings surface, and
        // a second pill for a setting most sessions never touch would cost the app bar more
        // than it is worth.
        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_offline_pin)
                setLabel(R.string.library_row_label)
                setSub(librarySub())
                setTrailingView(
                    ImageView(requireContext()).apply {
                        setImageResource(R.drawable.ic_chevron_right)
                        layoutParams = LinearLayout.LayoutParams(
                            context.dimen(R.dimen.icon_md),
                            context.dimen(R.dimen.icon_md),
                        )
                        imageTintList = ColorStateList.valueOf(context.color(R.color.text_low))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                )
                setOnClickListener {
                    LibrarySheetFragment().show(parentFragmentManager, LibrarySheetFragment.TAG)
                    // Two stacked sheets read as a mistake, and the library sheet owns the
                    // whole subject once it is open.
                    dismiss()
                }
            },
        )

        binding.footer.removeAllViews()

        // The one line the whole product is about.
        binding.footer.addView(
            TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Num_Meta)
                setTextColor(context.color(R.color.text_hi))
                text = getString(
                    R.string.about_per_hour,
                    Format.megabytes(snapshot.rung.mbPerHour),
                )
                setPadding(0, context.dimen(R.dimen.gap_button_tight), 0, 0)
            },
        )

        binding.footer.addView(
            TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Num_Caption)
                text = buildString {
                    append(getString(R.string.this_month))
                    append(" · ")
                    append(
                        getString(
                            R.string.month_usage,
                            Format.megabytes(session.megabytesStreamedThisMonth()),
                            Format.megabytes(session.megabytesOnWifiThisMonth()),
                        ),
                    )
                }
                setPadding(0, context.dimen(R.dimen.gap_button_tight), 0, 0)
            },
        )

        // Required attribution, not a credit we chose to give: TMDB's terms make it a
        // condition of using their API, and it belongs somewhere permanent rather than
        // only on a title screen the viewer may never open.
        binding.footer.addView(
            TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Caption)
                setTextColor(context.color(R.color.text_low))
                setText(R.string.tmdb_attribution)
                setPadding(0, context.dimen(R.dimen.gap_button_tight), 0, 0)
            },
        )
    }

    /** "3 folders", or an invitation when there are none. */
    private fun librarySub(): String {
        val count = prefs.libraryFolders.size
        return when {
            count == 0 -> getString(R.string.library_row_not_set_up)
            count == 1 -> getString(R.string.library_one_folder)
            else -> getString(R.string.library_folders_count, count)
        }
    }

    companion object {
        const val TAG = "data-sheet"
    }
}
