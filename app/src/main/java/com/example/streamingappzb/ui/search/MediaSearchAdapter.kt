package com.example.streamingappzb.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.databinding.ItemSearchResultBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.media.MediaFormat

/** Search results: 56x84 poster, title, then type · year · genre. */
class MediaSearchAdapter(
    private val onOpen: (MediaItem) -> Unit,
) : ListAdapter<MediaItem, MediaSearchAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ItemSearchResultBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) = with(binding) {
            poster.bind(item, showLabel = false)
            title.text = item.title
            meta.text = listOfNotNull(
                MediaFormat.typeLabel(root.context, item.type),
                item.year?.toString(),
                item.genres.firstOrNull(),
            ).joinToString(" · ")

            root.contentDescription = MediaFormat.contentDescription(root.context, item)
            root.setOnClickListener { onOpen(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(old: MediaItem, new: MediaItem) = old.key == new.key

        override fun areContentsTheSame(old: MediaItem, new: MediaItem) = old == new
    }
}
