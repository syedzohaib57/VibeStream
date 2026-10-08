package com.example.streamingappzb.ui.mylist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.data.session.SessionState
import com.example.streamingappzb.databinding.FragmentMylistBinding
import com.example.streamingappzb.databinding.ItemMylistPosterBinding
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.repository.CatalogRepository
import com.example.streamingappzb.domain.repository.MyListRepository
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.BaseListAdapter
import com.example.streamingappzb.ui.base.diffBy
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.nav.Navigator
import com.example.streamingappzb.ui.widget.GridSpacingDecoration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MyListViewModel(
    private val catalog: CatalogRepository,
    private val myList: MyListRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles: StateFlow<List<Title>> = _titles.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.ensureSeeded()
            // Ordered by the saved ids, so the grid stays most-recently-added first
            // (PRD §6.6) rather than falling back to catalogue order.
            session.myListIds.collect { ids ->
                val byId = catalog.titles().associateBy { it.id }
                _titles.value = ids.mapNotNull(byId::get)
            }
        }
    }

    fun remove(titleId: Int) {
        viewModelScope.launch { myList.toggle(titleId) }
    }
}

/**
 * My List (PRD §6.6): a 3-column grid, stored on this phone, no account needed. Long-press
 * a poster to take it off the list — the same gesture that puts it on from anywhere else.
 */
class MyListFragment : BaseFragment<FragmentMylistBinding>() {

    private val viewModel: MyListViewModel by viewModel()

    private val adapter = MyListAdapter(
        onClick = { id -> Navigator.title(requireContext(), id) },
        onLongClick = { id ->
            viewModel.remove(id)
            showToast(R.string.toast_removed_from_list)
        },
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentMylistBinding =
        FragmentMylistBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()

        val columns = resources.getInteger(R.integer.mylist_columns)
        binding.grid.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.grid.adapter = adapter
        binding.grid.addItemDecoration(
            GridSpacingDecoration(columns, resources.getDimensionPixelSize(R.dimen.mylist_gap)),
        )

        lifecycleScope.launch {
            viewModel.titles
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { titles ->
                    adapter.submitList(titles)
                    binding.emptyState.isVisible = titles.isEmpty()
                    binding.grid.isVisible = titles.isNotEmpty()
                }
        }
    }
}

private class MyListAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : BaseListAdapter<Title, ItemMylistPosterBinding>(diffBy { it.id }) {

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemMylistPosterBinding =
        ItemMylistPosterBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemMylistPosterBinding, item: Title, position: Int) {
        binding.root.bind(item)
        binding.root.onSingleClick { onClick(item.id) }
        binding.root.setOnLongClickListener {
            onLongClick(item.id)
            true
        }
    }
}
