package com.example.streamingappzb.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentSearchBinding
import com.example.streamingappzb.databinding.ItemGenreTileBinding
import com.example.streamingappzb.databinding.ItemSearchResultBinding
import com.example.streamingappzb.domain.model.Genre
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.BaseListAdapter
import com.example.streamingappzb.ui.base.diffBy
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.widget.GridSpacingDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Search (PRD §6.4).
 *
 * Before anything is typed the space fills with genre browse tiles rather than an empty
 * list; tapping one searches for that genre, exactly as the design does.
 */
class SearchFragment : BaseFragment<FragmentSearchBinding>() {

    private val viewModel: SearchViewModel by viewModel()

    private val results = ResultAdapter { id -> Navigator.title(requireContext(), id) }
    private val genreTiles = GenreAdapter { genre -> submitQuery(genre.name) }

    /** Guards the watcher against the state echo it just caused. */
    private var updatingFromState = false

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSearchBinding =
        FragmentSearchBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()

        binding.results.layoutManager = LinearLayoutManager(requireContext())
        binding.results.adapter = results

        val columns = resources.getInteger(R.integer.genre_columns)
        binding.genreGrid.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.genreGrid.adapter = genreTiles
        binding.genreGrid.addItemDecoration(
            GridSpacingDecoration(columns, resources.getDimensionPixelSize(R.dimen.mylist_gap)),
        )

        binding.query.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (!updatingFromState) viewModel.setQuery(s?.toString().orEmpty())
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            },
        )
        binding.btnClear.onSingleClick { submitQuery("") }

        observe()
    }

    private fun submitQuery(text: String) {
        updatingFromState = true
        binding.query.setText(text)
        binding.query.setSelection(text.length)
        updatingFromState = false
        viewModel.setQuery(text)
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    results.submitList(state.results)
                    genreTiles.submitList(state.genres)

                    binding.btnClear.isVisible = state.query.isNotEmpty()
                    binding.hint.isVisible = state.query.isBlank()
                    binding.genres.isVisible = state.showGenres
                    binding.results.isVisible = !state.showGenres && state.results.isNotEmpty()
                    binding.emptyState.isVisible = state.showEmptyState
                    if (state.showEmptyState) {
                        binding.emptyTitle.text =
                            getString(R.string.search_no_match, state.query)
                    }
                }
        }
    }
}

private class ResultAdapter(
    private val onClick: (Int) -> Unit,
) : BaseListAdapter<Title, ItemSearchResultBinding>(diffBy { it.id }) {

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemSearchResultBinding =
        ItemSearchResultBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemSearchResultBinding, item: Title, position: Int) {
        binding.poster.bind(item, showLabel = false)
        binding.title.text = item.title
        binding.meta.text = binding.root.resources.getString(
            R.string.search_result_meta,
            item.kind.name,
            item.year,
            item.genre,
        )
        binding.root.onSingleClick { onClick(item.id) }
    }
}

private class GenreAdapter(
    private val onClick: (Genre) -> Unit,
) : BaseListAdapter<Genre, ItemGenreTileBinding>(diffBy { it.name }) {

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemGenreTileBinding =
        ItemGenreTileBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemGenreTileBinding, item: Genre, position: Int) {
        binding.root.bindGenre(item)
        binding.genreName.text = item.name
        binding.root.onSingleClick { onClick(item) }
    }
}
