package com.example.streamingappzb.ui.discover

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ItemMediaPosterBinding
import com.example.streamingappzb.databinding.ItemMediaPosterRankedBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.media.MediaFormat
import com.example.streamingappzb.ui.widget.PosterView
import com.example.streamingappzb.ui.widget.RankNumeralView

/**
 * Posters inside a discover row, and the My List grid.
 *
 * A ranked row draws the same poster, badges and caption over a rank numeral, so both
 * layouts bind through one [Slot] rather than duplicating the binding.
 */
class MediaPosterAdapter(
    private val onOpen: (MediaItem) -> Unit,
    private val onLongPress: (MediaItem) -> Unit,
    /** Draw each poster over its position in the row — the Top 10 row. */
    private val ranked: Boolean = false,
) : ListAdapter<MediaItem, MediaPosterAdapter.Holder>(Diff) {

    /**
     * Rows on the discover screen share one [RecyclerView.RecycledViewPool] and the pool
     * is keyed by view type alone. A ranked row inflates a different layout, so it has to
     * report a different type — otherwise a plain row can be handed a ranked view and bind
     * against the wrong hierarchy.
     */
    override fun getItemViewType(position: Int): Int = if (ranked) TYPE_RANKED else TYPE_PLAIN

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(
            if (viewType == TYPE_RANKED) {
                ItemMediaPosterRankedBinding.inflate(inflater, parent, false).toSlot()
            } else {
                ItemMediaPosterBinding.inflate(inflater, parent, false).toSlot()
            },
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position), rank = position + 1)

    /**
     * The views a poster slot binds.
     *
     * Both layouts carry the same ids; only [numeral] is unique to the ranked one, and it
     * being null is what tells the holder which it has.
     */
    class Slot(
        val root: View,
        val poster: PosterView,
        val caption: TextView,
        val rating: TextView,
        val comingStrip: TextView,
        val numeral: RankNumeralView?,
    )

    inner class Holder(private val slot: Slot) : RecyclerView.ViewHolder(slot.root) {

        fun bind(item: MediaItem, rank: Int) = with(slot) {
            poster.bind(item)
            caption.text = MediaFormat.posterCaption(root.context, item)

            val score = item.ratingOutOfTen
            rating.isVisible = score != null
            rating.text = score

            val coming = MediaFormat.comingLabel(root.context, item)
            comingStrip.isVisible = coming != null
            comingStrip.text = coming

            numeral?.rank = rank

            root.setOnClickListener { onOpen(item) }
            root.setOnLongClickListener {
                onLongPress(item)
                true
            }

            // The poster's own description already names the title; the caption and badges
            // are read as part of the row, so the container announces once. A ranked slot
            // leads with its position, the way the numeral reads visually.
            val described = MediaFormat.contentDescription(root.context, item)
            root.contentDescription = if (numeral == null) {
                described
            } else {
                root.context.getString(R.string.cd_rank_prefix, rank, described)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(old: MediaItem, new: MediaItem) = old.key == new.key

        override fun areContentsTheSame(old: MediaItem, new: MediaItem) = old == new
    }

    private companion object {
        const val TYPE_PLAIN = 0
        const val TYPE_RANKED = 1
    }
}

private fun ItemMediaPosterBinding.toSlot() = MediaPosterAdapter.Slot(
    root = root,
    poster = poster,
    caption = caption,
    rating = rating,
    comingStrip = comingStrip,
    numeral = null,
)

private fun ItemMediaPosterRankedBinding.toSlot() = MediaPosterAdapter.Slot(
    root = root,
    poster = poster,
    caption = caption,
    rating = rating,
    comingStrip = comingStrip,
    numeral = rankNumeral,
)
