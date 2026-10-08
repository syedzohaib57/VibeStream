package com.example.streamingappzb.ui.upcoming

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.databinding.ItemMediaRowBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.discover.MediaPosterAdapter

/** One month per row, reusing the discover row layout so the two screens stay consistent. */
class UpcomingAdapter(
    private val onOpen: (MediaItem) -> Unit,
) : ListAdapter<UpcomingGroup, UpcomingAdapter.Holder>(Diff) {

    private val pool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMediaRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        pool,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ItemMediaRowBinding,
        sharedPool: RecyclerView.RecycledViewPool,
    ) : RecyclerView.ViewHolder(binding.root) {

        // Long-press to save is not offered here: nothing has been released yet, and a
        // My List of things you cannot watch is a different feature from a watchlist.
        private val posters = MediaPosterAdapter(onOpen, onLongPress = {})

        init {
            binding.posters.setRecycledViewPool(sharedPool)
            binding.posters.adapter = posters
            binding.posters.setHasFixedSize(true)
        }

        fun bind(group: UpcomingGroup) {
            binding.rowTitle.text = group.label
            posters.submitList(group.items)
        }
    }

    private object Diff : DiffUtil.ItemCallback<UpcomingGroup>() {
        override fun areItemsTheSame(old: UpcomingGroup, new: UpcomingGroup) =
            old.label == new.label

        override fun areContentsTheSame(old: UpcomingGroup, new: UpcomingGroup) = old == new
    }
}
