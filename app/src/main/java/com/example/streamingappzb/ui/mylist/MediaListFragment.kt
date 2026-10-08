package com.example.streamingappzb.ui.mylist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.FragmentMylistBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.base.BaseFragment
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.padTopForStatusBar
import com.example.streamingappzb.ui.discover.MediaPosterAdapter
import com.example.streamingappzb.ui.media.MediaDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** My List over real catalogue items: a 3-column grid, most recently added first. */
class MediaListFragment : BaseFragment<FragmentMylistBinding>() {

    private val viewModel: MediaListViewModel by viewModel()

    private val adapter = MediaPosterAdapter(
        onOpen = ::open,
        // Long-press removes, the mirror of the long-press that saved it.
        onLongPress = { viewModel.remove(it) },
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMylistBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.header.padTopForStatusBar()

        val columns = resources.getInteger(R.integer.mylist_columns)
        binding.grid.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.grid.adapter = adapter
        binding.grid.updatePadding(bottom = requireContext().dimen(R.dimen.bottom_nav_height))

        lifecycleScope.launch {
            viewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    adapter.submitList(state.items)
                    binding.emptyState.isVisible = state.isEmpty
                    binding.grid.isVisible = state.items.isNotEmpty()
                }
        }
    }

    private fun open(item: MediaItem) {
        startActivity(MediaDetailActivity.intent(requireContext(), item.id, item.type))
    }
}
