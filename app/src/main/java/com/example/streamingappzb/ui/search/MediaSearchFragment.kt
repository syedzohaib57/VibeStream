package com.example.streamingappzb.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentSearchBinding
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.browse.BrowseActivity
import com.example.streamingappzb.ui.media.MediaDetailActivity
import com.example.streamingappzb.ui.widget.GridSpacingDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Search over the real catalogue.
 *
 * Reuses the original search layout: the same 88dp header, field and result rows, and the
 * genre browse grid as the empty state. That grid was hidden while its tiles were coloured
 * from the sample catalogue's shipped palette, which TMDB has no counterpart for; it is back
 * now that the genres come from TMDB and the tile colour is derived from the name.
 */
class MediaSearchFragment : BaseFragment<FragmentSearchBinding>() {

    private val viewModel: MediaSearchViewModel by viewModel()

    private val adapter = MediaSearchAdapter(::open)

    private val genreAdapter = MediaGenreAdapter(::openGenre)

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentSearchBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()
        binding.query.hint = getString(R.string.search_hint_media)

        binding.results.layoutManager = LinearLayoutManager(requireContext())
        binding.results.adapter = adapter

        binding.query.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
        binding.btnClear.onSingleClick {
            binding.query.setText("")
            viewModel.clear()
        }

        val columns = resources.getInteger(R.integer.genre_columns)
        binding.genreGrid.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.genreGrid.adapter = genreAdapter
        binding.genreGrid.addItemDecoration(
            GridSpacingDecoration(columns, requireContext().dimen(R.dimen.mylist_gap)),
        )
        binding.genreGrid.updatePadding(
            bottom = requireContext().dimen(R.dimen.bottom_nav_height),
        )

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    adapter.submitList(state.visible)
                    genreAdapter.submitList(state.genres)
                    binding.results.isVisible = state.visible.isNotEmpty()
                    binding.btnClear.isVisible = state.query.isNotEmpty()
                    binding.emptyState.isVisible = state.showEmptyState
                    binding.emptyTitle.text =
                        getString(R.string.search_no_match, state.query)
                    // The browse grid *is* the empty state: before typing, it is the only
                    // thing on this tab a viewer can act on.
                    binding.genres.isVisible = state.showSuggestions && state.genres.isNotEmpty()
                    binding.hint.isVisible = state.showSuggestions && state.genres.isEmpty()
                }
        }
    }

    private fun openGenre(genre: MediaGenre) {
        hideKeyboard()
        startActivity(BrowseActivity.forGenre(requireContext(), genre))
    }

    /** Dismisses the IME without an extension the project does not have. */
    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.query.windowToken, 0)
    }

    private fun open(item: MediaItem) {
        hideKeyboard()
        startActivity(MediaDetailActivity.intent(requireContext(), item.id, item.type))
    }
}
