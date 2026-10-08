package com.example.streamingappzb.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.databinding.ItemGenreTileBinding
import com.example.streamingappzb.domain.model.MediaGenre
import com.example.streamingappzb.ui.base.onSingleClick

/**
 * The genre browse grid, over TMDB's own genre list.
 *
 * This grid was previously hidden: it existed, but its tiles were coloured from the sample
 * catalogue's shipped palette, which has no counterpart in TMDB. Now that the genres are
 * real, the tile colour is derived from the name instead and the grid is useful again — it
 * is the one thing a viewer can do on the Search tab before typing anything.
 */
class MediaGenreAdapter(
    private val onOpen: (MediaGenre) -> Unit,
) : ListAdapter<MediaGenre, MediaGenreAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemGenreTileBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onOpen,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemGenreTileBinding,
        private val onOpen: (MediaGenre) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(genre: MediaGenre) = with(binding) {
            root.bindGenre(genre)
            genreName.text = genre.name
            root.onSingleClick { onOpen(genre) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MediaGenre>() {
        override fun areItemsTheSame(old: MediaGenre, new: MediaGenre) = old.key == new.key

        override fun areContentsTheSame(old: MediaGenre, new: MediaGenre) = old == new
    }
}
