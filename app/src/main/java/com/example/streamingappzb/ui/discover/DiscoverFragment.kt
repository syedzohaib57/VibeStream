package com.example.streamingappzb.ui.discover

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentDiscoverBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.main.MainActivity
import com.example.streamingappzb.ui.widget.MhTab
import com.example.streamingappzb.ui.media.MediaDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Discover — the Home tab, over TMDB and AniList.
 *
 * The app-bar treatment is carried over unchanged: a gradient over the hero that crossfades
 * to solid after 420dp of scroll, via a TransitionDrawable on the bar's own background
 * rather than stacked full-height views (which would dim the whole list).
 */
class DiscoverFragment : BaseFragment<FragmentDiscoverBinding>() {

    private val viewModel: DiscoverViewModel by viewModel()

    private val adapter = DiscoverAdapter(
        onOpen = { open(it) },
        onLongPress = { viewModel.toggleMyList(it) },
        onHeroWatch = { open(it) },
        onHeroMyList = { viewModel.toggleMyList(it) },
    )

    private val tabViews = linkedMapOf<MediaType?, TextView>()
    private var appBarBackground: TransitionDrawable? = null
    private var solid = false
    private var scrolled = 0

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentDiscoverBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        setUpAppBarBackground()
        applyInsets()
        buildTabs()

        binding.rows.layoutManager = LinearLayoutManager(requireContext())
        binding.rows.adapter = adapter
        binding.rows.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    scrolled = (scrolled + dy).coerceAtLeast(0)
                    setAppBarSolid(scrolled >= requireContext().dimen(R.dimen.appbar_scroll_solid_at))
                }
            },
        )

        binding.refresh.setColorSchemeColors(color(R.color.ember500))
        binding.refresh.setProgressBackgroundColorSchemeColor(color(R.color.night800))
        binding.refresh.setOnRefreshListener { viewModel.refresh() }

        binding.btnSearch.onSingleClick {
            (activity as? MainActivity)?.selectTab(MhTab.Search)
        }
        binding.dataPill.onSingleClick {
            com.example.streamingappzb.ui.data.DataSheetFragment()
                .show(parentFragmentManager, DATA_SHEET_TAG)
        }

        observe()
    }

    /** All · Films · Series · Anime. */
    private fun buildTabs() {
        val tabs = listOf(
            null to R.string.tab_all,
            MediaType.Movie to R.string.tab_films,
            MediaType.Tv to R.string.tab_series,
            MediaType.Anime to R.string.tab_anime,
        )
        binding.chips.removeAllViews()
        tabViews.clear()
        for ((type, labelRes) in tabs) {
            val chip = TextView(requireContext(), null, 0, R.style.Widget_Mh_Chip).apply {
                setText(labelRes)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.dimen(R.dimen.chip_touch_height),
                ).apply { marginEnd = context.dimen(R.dimen.gap_poster) }
                onSingleClick { viewModel.selectTab(type) }
            }
            tabViews[type] = chip
            binding.chips.addView(chip)
        }
    }

    private fun applyTabSelection(selected: MediaType?) {
        for ((type, chip) in tabViews) {
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
                    adapter.submitList(state.items)
                    applyTabSelection(state.tab)
                    binding.dataPill.render(state.network, state.saver)
                    binding.loading.isVisible = state.loading && !state.needsApiKey
                    binding.refresh.isRefreshing = state.refreshing
                    binding.offlineBanner.isVisible = state.staleOffline
                    binding.setupNotice.isVisible = state.needsApiKey
                    // Nothing below the notice is usable without a key.
                    binding.refresh.isVisible = !state.needsApiKey
                }
        }

        lifecycleScope.launch {
            viewModel.toast
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { messageRes ->
                    if (messageRes != null) {
                        showToast(messageRes)
                        viewModel.consumeToast()
                    }
                }
        }
    }

    private fun open(item: MediaItem) {
        viewModel.onOpened(item)
        startActivity(MediaDetailActivity.intent(requireContext(), item.id, item.type))
    }

    private fun setUpAppBarBackground() {
        val gradient = ContextCompat.getDrawable(requireContext(), R.drawable.bg_appbar_gradient)
        val fill = ColorDrawable(color(R.color.appbar_solid))
        appBarBackground = TransitionDrawable(arrayOf(gradient, fill)).apply {
            isCrossFadeEnabled = true
            binding.appBarContent.background = this
        }
    }

    private fun setAppBarSolid(next: Boolean) {
        if (next == solid) return
        solid = next
        val duration = resources.getInteger(R.integer.dur_appbar_fill)
        if (next) {
            appBarBackground?.startTransition(duration)
        } else {
            appBarBackground?.reverseTransition(duration)
        }
    }

    private fun applyInsets() {
        binding.appBarContent.padTopForStatusBar()
        binding.rows.updatePadding(bottom = requireContext().dimen(R.dimen.bottom_nav_height))
    }

    private companion object {
        const val DATA_SHEET_TAG = "data-sheet"
    }
}
