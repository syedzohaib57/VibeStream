package com.example.streamingappzb.ui.home

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentHomeBinding
import com.example.streamingappzb.domain.model.Kind
import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.dp
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.main.MainActivity
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.widget.MhTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Home (PRD §6.1).
 *
 * The app bar floats over the banner and turns solid **after 420dp of scroll**, crossfaded
 * over 220ms (acceptance item 5). Scroll distance is accumulated from `dy` rather than read
 * from `computeVerticalScrollOffset`, which only estimates once items of different heights
 * have been recycled.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModel()

    private lateinit var adapter: HomeAdapter

    private var scrolled = 0
    private var solid = false
    private var solidThresholdPx = 0
    private val chipViews = mutableMapOf<String, TextView>()

    /**
     * The app bar's background: the resting gradient, crossfading to 94% `night900`.
     *
     * A TransitionDrawable rather than two stacked Views, because a `match_parent` View
     * inside a `wrap_content` FrameLayout measures to the whole screen — which silently
     * painted the bar's 72%-black gradient over all of Home.
     */
    private var appBarBackground: TransitionDrawable? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        solidThresholdPx = requireContext().dimen(R.dimen.appbar_scroll_solid_at)

        binding.appBarContent.padTopForStatusBar()
        setUpAppBarBackground()
        buildChips()
        setUpList()
        setUpAppBarActions()
        observe()
    }

    // ---------------------------------------------------------------- setup

    private fun setUpList() {
        adapter = HomeAdapter(
            HomeCallbacks(
                onHeroPlay = { title, progress ->
                    viewModel.onHeroPlay(title.id, progress != null)
                    Navigator.play(
                        requireContext(),
                        titleId = title.id,
                        episode = progress?.episode ?: 1,
                        positionSeconds = progress?.positionSeconds ?: 0,
                    )
                },
                onToggleMyList = viewModel::toggleMyList,
                onOpenTitle = { id ->
                    viewModel.onTitleOpened(id)
                    Navigator.title(requireContext(), id)
                },
                onResume = { item ->
                    Navigator.play(
                        requireContext(),
                        titleId = item.title.id,
                        episode = item.progress.episode,
                        positionSeconds = item.progress.positionSeconds,
                    )
                },
            ),
        )

        binding.rows.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@HomeFragment.adapter
            // The design hides scrollbars (.mh-scroll). Set in code as well as XML because
            // some OEM themes re-enable them.
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            // The design's 12dp tail; the bottom nav is a sibling, so no inset is needed.
            setPadding(paddingLeft, paddingTop, paddingRight, context.dp(12))
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        scrolled = (scrolled + dy).coerceAtLeast(0)
                        applyAppBarFill(scrolled >= solidThresholdPx)
                    }
                },
            )
        }
    }

    private fun setUpAppBarActions() {
        binding.dataPill.onSingleClick {
            com.example.streamingappzb.ui.data.DataSheetFragment()
                .show(parentFragmentManager, DATA_SHEET_TAG)
        }
        binding.btnDownloads.onSingleClick {
            (activity as? MainActivity)?.selectTab(MhTab.Downloads)
        }
        binding.btnSearch.onSingleClick {
            (activity as? MainActivity)?.selectTab(MhTab.Search)
        }
    }

    /** All · Dramas · Films · Documentaries (PRD §6.1). */
    private fun buildChips() {
        val chips = listOf(
            ChipKey.ALL to R.string.chip_all,
            Kind.Drama.name to R.string.chip_dramas,
            Kind.Film.name to R.string.chip_films,
            Kind.Documentary.name to R.string.chip_documentaries,
        )
        binding.chips.removeAllViews()
        chipViews.clear()
        for ((key, labelRes) in chips) {
            // The 4-argument constructor applies the style, so the chip's metrics live in
            // styles.xml rather than being restated here.
            val chip = TextView(requireContext(), null, 0, R.style.Widget_Mh_Chip).apply {
                setText(labelRes)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.dimen(R.dimen.chip_touch_height),
                ).apply { marginEnd = context.dimen(R.dimen.gap_poster) }
                onSingleClick { viewModel.selectChip(key) }
            }
            chipViews[key] = chip
            binding.chips.addView(chip)
        }
    }

    // ---------------------------------------------------------------- state

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    adapter.submitList(state.items)
                    applyChipSelection(state.chip)
                    binding.dataPill.render(state.network, state.saver)
                    binding.downloadDot.isVisible = state.activeDownloads > 0
                    binding.offlineBanner.isVisible = state.network == NetworkState.Offline
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

    private fun applyChipSelection(selectedKey: String) {
        for ((key, chip) in chipViews) {
            val on = key == selectedKey
            // The inset variants: 32dp of paint inside a 44dp target.
            chip.setBackgroundResource(
                if (on) R.drawable.bg_chip_selected_inset else R.drawable.bg_chip_unselected_inset,
            )
            chip.setTextColor(
                chip.color(if (on) R.color.chip_selected_text else R.color.text_hi),
            )
            chip.isSelected = on
        }
    }

    private fun setUpAppBarBackground() {
        val gradient = ContextCompat.getDrawable(requireContext(), R.drawable.bg_appbar_gradient)
        val fill = ColorDrawable(color(R.color.appbar_solid))
        appBarBackground = TransitionDrawable(arrayOf(gradient, fill)).apply {
            isCrossFadeEnabled = true
            binding.appBarContent.background = this
        }
    }

    /** 94% night900 fades in over the gradient; nothing swaps abruptly. */
    private fun applyAppBarFill(shouldBeSolid: Boolean) {
        if (solid == shouldBeSolid) return
        solid = shouldBeSolid
        val duration = resources.getInteger(R.integer.dur_appbar_fill)
        appBarBackground?.apply {
            if (shouldBeSolid) startTransition(duration) else reverseTransition(duration)
        }
    }

    override fun onDestroyView() {
        appBarBackground = null
        super.onDestroyView()
    }

    private companion object {
        const val DATA_SHEET_TAG = "data-sheet"
    }
}
