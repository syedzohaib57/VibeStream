package com.example.streamingappzb.ui.upcoming

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentUpcomingBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.media.MediaDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Upcoming releases, grouped by month. */
class UpcomingFragment : BaseFragment<FragmentUpcomingBinding>() {

    private val viewModel: UpcomingViewModel by viewModel()

    private val adapter = UpcomingAdapter(::open)

    private val filterViews = linkedMapOf<MediaType?, TextView>()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentUpcomingBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()
        buildFilters()

        binding.groups.layoutManager = LinearLayoutManager(requireContext())
        binding.groups.adapter = adapter

        binding.refresh.setColorSchemeColors(color(R.color.ember500))
        binding.refresh.setProgressBackgroundColorSchemeColor(color(R.color.night800))
        binding.refresh.setOnRefreshListener { viewModel.refresh() }

        // The header floats over the list, so the first group clears it.
        binding.groups.post {
            binding.groups.updatePadding(
                top = binding.header.height,
                bottom = requireContext().dimen(R.dimen.bottom_nav_height),
            )
        }

        observe()
    }

    private fun buildFilters() {
        val filters = listOf(
            null to R.string.tab_all,
            MediaType.Movie to R.string.tab_films,
            MediaType.Tv to R.string.tab_series,
            MediaType.Anime to R.string.tab_anime,
        )
        binding.filters.removeAllViews()
        filterViews.clear()
        for ((type, labelRes) in filters) {
            val chip = TextView(requireContext(), null, 0, R.style.Widget_Mh_Chip).apply {
                setText(labelRes)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.dimen(R.dimen.chip_touch_height),
                ).apply { marginEnd = context.dimen(R.dimen.gap_poster) }
                onSingleClick { viewModel.setFilter(type) }
            }
            filterViews[type] = chip
            binding.filters.addView(chip)
        }
    }

    private fun applyFilterSelection(selected: MediaType?) {
        for ((type, chip) in filterViews) {
            val on = type == selected
            chip.setBackgroundResource(
                if (on) R.drawable.bg_chip_selected_inset else R.drawable.bg_chip_unselected_inset,
            )
            chip.setTextColor(color(if (on) R.color.chip_selected_text else R.color.text_hi))
            chip.isSelected = on
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    adapter.submitList(state.groups)
                    applyFilterSelection(state.filter)
                    binding.loading.isVisible = state.loading && state.groups.isEmpty()
                    binding.refresh.isRefreshing = false
                    binding.emptyState.isVisible = state.isEmpty
                }
        }
    }

    private fun open(item: MediaItem) {
        startActivity(MediaDetailActivity.intent(requireContext(), item.id, item.type))
    }
}
