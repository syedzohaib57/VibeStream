package com.example.streamingappzb.ui.browse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ActivityBrowseBinding
import com.example.streamingappzb.domain.model.BrowseSort
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.Studio
import com.example.streamingappzb.ui.base.BaseActivity
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.discover.MediaPosterAdapter
import com.example.streamingappzb.ui.media.MediaDetailActivity
import com.example.streamingappzb.ui.widget.GridSpacingDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * A browse grid: every title in a genre, from a studio, or on a network.
 *
 * One screen for all three because they are the same `/discover` request with a different
 * parameter — a separate Activity per axis would be three copies of the same paging and
 * sorting code. Which axis it is comes from the intent, and only one is ever set.
 */
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val genreId by lazy { intent.getIntExtra(EXTRA_GENRE_ID, 0).takeIf { it > 0 } }
    private val companyId by lazy { intent.getIntExtra(EXTRA_COMPANY_ID, 0).takeIf { it > 0 } }
    private val networkId by lazy { intent.getIntExtra(EXTRA_NETWORK_ID, 0).takeIf { it > 0 } }
    private val heading by lazy { intent.getStringExtra(EXTRA_HEADING).orEmpty() }
    private val mediaType by lazy {
        runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_TYPE).orEmpty()) }
            .getOrDefault(MediaType.Movie)
    }

    private val viewModel: BrowseViewModel by viewModel {
        parametersOf(genreId, companyId, networkId, mediaType, heading)
    }

    private val adapter = MediaPosterAdapter(
        onOpen = ::open,
        onLongPress = { viewModel.toggleMyList(it) },
    )

    private val sortChips = linkedMapOf<BrowseSort, TextView>()

    override fun inflateBinding(inflater: LayoutInflater) = ActivityBrowseBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()
        binding.btnBack.onSingleClick { finish() }
        binding.title.text = heading

        val columns = resources.getInteger(R.integer.mylist_columns)
        binding.grid.layoutManager = GridLayoutManager(this, columns)
        binding.grid.adapter = adapter
        binding.grid.addItemDecoration(
            GridSpacingDecoration(columns, dimen(R.dimen.mylist_gap)),
        )
        binding.grid.addOnScrollListener(PagingListener(columns))

        buildSortChips()
        observe()
    }

    /** Popular · Top rated · Newest. Three options, so chips rather than a menu. */
    private fun buildSortChips() {
        val labels = listOf(
            BrowseSort.Popular to R.string.sort_popular,
            BrowseSort.TopRated to R.string.sort_top_rated,
            BrowseSort.Newest to R.string.sort_newest,
        )
        binding.sortChips.removeAllViews()
        sortChips.clear()
        for ((sort, labelRes) in labels) {
            val chip = TextView(this, null, 0, R.style.Widget_Mh_Chip).apply {
                setText(labelRes)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dimen(R.dimen.chip_touch_height),
                ).apply { marginEnd = dimen(R.dimen.gap_poster) }
                onSingleClick { viewModel.setSort(sort) }
            }
            sortChips[sort] = chip
            binding.sortChips.addView(chip)
        }
    }

    private fun applySortSelection(selected: BrowseSort) {
        for ((sort, chip) in sortChips) {
            val on = sort == selected
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
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    adapter.submitList(state.items)
                    applySortSelection(state.sort)
                    binding.loading.isVisible = state.loading
                    binding.appending.isVisible = state.appending
                    binding.emptyState.isVisible = state.isEmpty
                    binding.grid.isVisible = state.items.isNotEmpty()
                }
        }
    }

    /**
     * Asks for the next page a couple of rows before the end, so the grid keeps moving
     * rather than stopping at the bottom and then filling in.
     */
    private inner class PagingListener(private val columns: Int) : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val layout = rv.layoutManager as? GridLayoutManager ?: return
            val lastVisible = layout.findLastVisibleItemPosition()
            if (lastVisible >= layout.itemCount - columns * PREFETCH_ROWS) {
                viewModel.loadMore()
            }
        }
    }

    private fun open(item: MediaItem) {
        viewModel.onOpened(item)
        startActivity(MediaDetailActivity.intent(this, item.id, item.type))
    }

    companion object {
        private const val EXTRA_GENRE_ID = "genre_id"
        private const val EXTRA_COMPANY_ID = "company_id"
        private const val EXTRA_NETWORK_ID = "network_id"
        private const val EXTRA_TYPE = "media_type"
        private const val EXTRA_HEADING = "heading"

        /** Two rows ahead: enough that the next page lands before the viewer reaches it. */
        private const val PREFETCH_ROWS = 2

        fun forGenre(context: Context, genre: MediaGenre): Intent =
            Intent(context, BrowseActivity::class.java)
                .putExtra(EXTRA_GENRE_ID, genre.id)
                .putExtra(EXTRA_TYPE, genre.scope.mediaType.name)
                .putExtra(EXTRA_HEADING, genre.name)

        /**
         * A studio or broadcaster. The type follows [Studio.isNetwork]: networks exist only
         * for television, and a company's catalogue is overwhelmingly film.
         */
        fun forStudio(context: Context, studio: Studio): Intent =
            Intent(context, BrowseActivity::class.java)
                .putExtra(
                    if (studio.isNetwork) EXTRA_NETWORK_ID else EXTRA_COMPANY_ID,
                    studio.id,
                )
                .putExtra(
                    EXTRA_TYPE,
                    if (studio.isNetwork) MediaType.Tv.name else MediaType.Movie.name,
                )
                .putExtra(EXTRA_HEADING, studio.name)
    }
}
