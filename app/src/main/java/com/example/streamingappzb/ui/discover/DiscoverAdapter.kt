package com.example.streamingappzb.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ItemMediaHeroBinding
import com.example.streamingappzb.databinding.ItemMediaRowBinding
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.media.MediaFormat

/**
 * The discover list: one hero then N poster rows.
 *
 * Nested horizontal lists share a [RecyclerView.RecycledViewPool], so scrolling down
 * through eight rows reuses poster views instead of inflating eighty of them.
 */
class DiscoverAdapter(
    private val onOpen: (MediaItem) -> Unit,
    private val onLongPress: (MediaItem) -> Unit,
    private val onHeroWatch: (MediaItem) -> Unit,
    private val onHeroMyList: (MediaItem) -> Unit,
) : ListAdapter<DiscoverItem, RecyclerView.ViewHolder>(Diff) {

    private val posterPool = RecyclerView.RecycledViewPool()

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is DiscoverItem.Hero -> TYPE_HERO
        // A ranked row is its own type so a recycled holder always already carries the
        // right poster adapter, rather than having one swapped in on bind.
        is DiscoverItem.Row -> if (item.row.isRankedRow) TYPE_ROW_RANKED else TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HERO -> HeroHolder(ItemMediaHeroBinding.inflate(inflater, parent, false))
            else -> RowHolder(
                ItemMediaRowBinding.inflate(inflater, parent, false),
                posterPool,
                ranked = viewType == TYPE_ROW_RANKED,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DiscoverItem.Hero -> (holder as HeroHolder).bind(item)
            is DiscoverItem.Row -> (holder as RowHolder).bind(item)
        }
    }

    inner class HeroHolder(
        private val binding: ItemMediaHeroBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hero: DiscoverItem.Hero) = with(binding) {
            val item = hero.item
            heroPoster.bind(item, showLabel = false, backdrop = true)
            heroTitle.text = item.title
            heroGenres.text = item.genres.joinToString(" • ")
            heroGenres.isVisible(item.genres.isNotEmpty())

            MediaFormat.fillHeroMeta(heroMeta, item)

            btnHeroWatch.setOnClickListener { onHeroWatch(item) }
            btnHeroInfo.setOnClickListener { onOpen(item) }
            heroPoster.setOnClickListener { onOpen(item) }

            btnHeroMyList.setIconResource(
                if (hero.inMyList) R.drawable.ic_check else R.drawable.ic_add,
            )
            btnHeroMyList.contentDescription = root.context.getString(
                if (hero.inMyList) R.string.cd_remove_from_list else R.string.cd_add_to_list,
            )
            btnHeroMyList.setOnClickListener { onHeroMyList(item) }
        }
    }

    inner class RowHolder(
        private val binding: ItemMediaRowBinding,
        pool: RecyclerView.RecycledViewPool,
        ranked: Boolean,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val posterAdapter = MediaPosterAdapter(onOpen, onLongPress, ranked = ranked)

        init {
            binding.posters.setRecycledViewPool(pool)
            binding.posters.adapter = posterAdapter
            binding.posters.setHasFixedSize(true)
        }

        fun bind(row: DiscoverItem.Row) {
            binding.rowTitle.text = row.row.title
            // `rankedItems` is the plain list for every other row, so this stays one call.
            posterAdapter.submitList(row.row.rankedItems)
        }
    }

    private object Diff : DiffUtil.ItemCallback<DiscoverItem>() {
        override fun areItemsTheSame(old: DiscoverItem, new: DiscoverItem): Boolean = when {
            old is DiscoverItem.Hero && new is DiscoverItem.Hero -> true
            old is DiscoverItem.Row && new is DiscoverItem.Row -> old.row.key == new.row.key
            else -> false
        }

        override fun areContentsTheSame(old: DiscoverItem, new: DiscoverItem): Boolean = old == new
    }

    private companion object {
        const val TYPE_HERO = 0
        const val TYPE_ROW = 1
        const val TYPE_ROW_RANKED = 2
    }
}

private fun android.view.View.isVisible(visible: Boolean) {
    visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
}
