package com.example.streamingappzb.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ItemContinueCardBinding
import com.example.streamingappzb.databinding.ItemHomeContinueBinding
import com.example.streamingappzb.databinding.ItemHomeHeroBinding
import com.example.streamingappzb.databinding.ItemHomeRowBinding
import com.example.streamingappzb.databinding.ItemPosterBinding
import com.example.streamingappzb.databinding.ItemPosterRankedBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.ContinueItem
import com.example.streamingappzb.domain.model.HomeRow
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.ui.base.BaseListAdapter
import com.example.streamingappzb.ui.base.BindingViewHolder
import com.example.streamingappzb.ui.base.diffBy
import com.example.streamingappzb.ui.base.onSingleClick

/** What Home's rows can ask the fragment to do. */
class HomeCallbacks(
    val onHeroPlay: (Title, Progress?) -> Unit,
    val onToggleMyList: (Int) -> Unit,
    val onOpenTitle: (Int) -> Unit,
    val onResume: (ContinueItem) -> Unit,
)

/**
 * Home's vertical list: the hero, Continue watching, then the curated rows.
 *
 * Which rows exist and how many is decided by
 * [com.example.streamingappzb.domain.usecase.GetHomeFeedUseCase] — the eight-row cap and
 * dropping rows emptied by filtering are rules, not layout concerns.
 */
class HomeAdapter(
    private val callbacks: HomeCallbacks,
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(DIFF) {

    /** Every poster row draws the same item type, so one pool serves them all. */
    private val posterPool = RecyclerView.RecycledViewPool()

    /** Horizontal scroll offsets, so a row keeps its place across a chip change. */
    private val rowScroll = mutableMapOf<String, Int>()

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is HomeItem.Hero -> TYPE_HERO
        is HomeItem.Continue -> TYPE_CONTINUE
        // A ranked row is its own type so a recycled holder always already carries the
        // right inner adapter, rather than having one swapped in on bind.
        is HomeItem.Row -> if (item.row.isRankedRow) TYPE_ROW_RANKED else TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HERO -> HeroHolder(ItemHomeHeroBinding.inflate(inflater, parent, false), callbacks)
            TYPE_CONTINUE -> ContinueHolder(
                ItemHomeContinueBinding.inflate(inflater, parent, false),
                callbacks,
            )

            else -> RowHolder(
                ItemHomeRowBinding.inflate(inflater, parent, false),
                callbacks,
                posterPool,
                rowScroll,
                ranked = viewType == TYPE_ROW_RANKED,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeItem.Hero -> (holder as HeroHolder).bind(item)
            is HomeItem.Continue -> (holder as ContinueHolder).bind(item)
            is HomeItem.Row -> (holder as RowHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? RowHolder)?.saveScroll()
        super.onViewRecycled(holder)
    }

    private companion object {
        const val TYPE_HERO = 0
        const val TYPE_CONTINUE = 1
        const val TYPE_ROW = 2
        const val TYPE_ROW_RANKED = 3

        val DIFF = object : DiffUtil.ItemCallback<HomeItem>() {
            override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean = when {
                oldItem is HomeItem.Hero && newItem is HomeItem.Hero -> true
                oldItem is HomeItem.Continue && newItem is HomeItem.Continue -> true
                oldItem is HomeItem.Row && newItem is HomeItem.Row ->
                    oldItem.row.key == newItem.row.key

                else -> false
            }

            override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean =
                oldItem == newItem
        }
    }
}

// ---------------------------------------------------------------- Hero

private class HeroHolder(
    private val binding: ItemHomeHeroBinding,
    private val callbacks: HomeCallbacks,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HomeItem.Hero) {
        val title = item.title
        val res = binding.root.resources

        binding.heroPoster.bind(title, showLabel = false)
        // The halo stands in for key art; real art needs no help.
        binding.heroRosette.isVisible = title.artUrl == null
        binding.heroTitle.text = title.title
        binding.heroYear.text = title.year.toString()

        // "New episodes" is cyan and appears only on a fresh series (acceptance item 4).
        binding.heroNewEpisodes.isVisible = title.showsNewEpisodes

        binding.heroRuntime.text = if (title.isFilm) {
            val (hours, minutes) = Format.runtimeParts(title.runtimeMinutes ?: 0)
            res.getString(R.string.runtime_h_m, hours, minutes)
        } else {
            res.getQuantityString(R.plurals.episodes_count, title.totalEpisodes, title.totalEpisodes)
        }

        binding.heroGenres.text = title.genreList.joinToString(GENRE_SEPARATOR)

        val progress = item.progress
        binding.btnHeroPlay.text = when {
            progress == null -> res.getString(R.string.action_play)
            title.isFilm -> res.getString(R.string.action_resume)
            else -> res.getString(R.string.hero_resume_episode, progress.episode)
        }
        binding.btnHeroPlay.contentDescription = if (progress == null) {
            res.getString(R.string.cd_play_title, title.title)
        } else {
            res.getString(R.string.cd_resume_title, title.title)
        }
        binding.btnHeroPlay.onSingleClick { callbacks.onHeroPlay(title, progress) }

        binding.btnHeroMyList.setIconResource(
            if (item.inMyList) R.drawable.ic_check else R.drawable.ic_add,
        )
        binding.btnHeroMyList.contentDescription = res.getString(
            if (item.inMyList) R.string.cd_remove_from_list else R.string.cd_add_to_list,
        )
        binding.btnHeroMyList.onSingleClick { callbacks.onToggleMyList(title.id) }

        binding.btnHeroInfo.onSingleClick { callbacks.onOpenTitle(title.id) }
    }

    private companion object {
        /** The design joins hero genres with a bullet. */
        const val GENRE_SEPARATOR = " • "
    }
}

// ---------------------------------------------------------------- Continue watching

private class ContinueHolder(
    private val binding: ItemHomeContinueBinding,
    callbacks: HomeCallbacks,
) : RecyclerView.ViewHolder(binding.root) {

    private val adapter = ContinueAdapter(callbacks)

    init {
        binding.continueCards.adapter = adapter
        binding.continueCards.setHasFixedSize(true)
    }

    fun bind(item: HomeItem.Continue) = adapter.submitList(item.items)
}

private class ContinueAdapter(
    private val callbacks: HomeCallbacks,
) : BaseListAdapter<ContinueItem, ItemContinueCardBinding>(diffBy { it.title.id }) {

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemContinueCardBinding =
        ItemContinueCardBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemContinueCardBinding, item: ContinueItem, position: Int) {
        val res = binding.root.resources
        binding.poster.bind(item.title, showLabel = false)
        binding.progress.progress = Format.percent(item.fraction)
        binding.title.text = item.title.title

        // "{m} min left" for a film, "E{n} · {m} min left" for an episode.
        binding.meta.text = if (item.title.isFilm) {
            res.getQuantityString(R.plurals.min_left_plural, item.minutesLeft, item.minutesLeft)
        } else {
            res.getString(R.string.episode_min_left, item.progress.episode, item.minutesLeft)
        }

        binding.root.contentDescription = res.getString(R.string.cd_resume_title, item.title.title)
        binding.root.onSingleClick { callbacks.onResume(item) }
        binding.btnInfo.onSingleClick { callbacks.onOpenTitle(item.title.id) }
    }
}

// ---------------------------------------------------------------- Curated row

private class RowHolder(
    private val binding: ItemHomeRowBinding,
    private val callbacks: HomeCallbacks,
    pool: RecyclerView.RecycledViewPool,
    private val rowScroll: MutableMap<String, Int>,
    ranked: Boolean,
) : RecyclerView.ViewHolder(binding.root) {

    /**
     * A ranked row draws a different item, so it needs a different adapter. The holder is
     * built for one or the other and never switches, which is why the row's kind is a
     * constructor argument and not something [bind] decides.
     */
    private val adapter: ListAdapter<Title, *> =
        if (ranked) RankedPosterAdapter(callbacks) else PosterAdapter(callbacks)

    private var rowKey: String? = null

    init {
        binding.posters.setRecycledViewPool(pool)
        binding.posters.adapter = adapter
        binding.posters.setHasFixedSize(true)
    }

    fun bind(item: HomeItem.Row) {
        val row = item.row
        val res = binding.root.resources
        rowKey = row.key

        // A shipped row prefers its localised heading; anything a server invents falls
        // back to the title it sent (PRD §8, Localisation).
        binding.rowTitle.text = ROW_TITLES[row.key]
            ?.let(res::getString)
            ?: row.fallbackTitle

        val isNew = row.isNewRow
        binding.newBadge.isVisible = isNew
        binding.rowChevron.isVisible = !isNew
        if (isNew) {
            binding.newCount.text =
                res.getQuantityString(R.plurals.new_count, row.titles.size, row.titles.size)
        }

        (adapter as? PosterAdapter)?.showNewStrip = isNew
        // `rankedTitles` is the plain list for every other row, so this stays one call.
        adapter.submitList(row.rankedTitles)

        (binding.posters.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(0, -(rowScroll[row.key] ?: 0))
    }

    fun saveScroll() {
        rowKey?.let { rowScroll[it] = binding.posters.computeHorizontalScrollOffset() }
    }

    private companion object {
        val ROW_TITLES = mapOf(
            HomeRow.KEY_NEW to R.string.row_new,
            HomeRow.KEY_TOP10 to R.string.row_top10,
            "classic" to R.string.row_classic,
            "regional" to R.string.row_regional,
            "films" to R.string.row_films,
            "docs" to R.string.row_docs,
        )
    }
}

private class PosterAdapter(
    private val callbacks: HomeCallbacks,
) : BaseListAdapter<Title, ItemPosterBinding>(diffBy { it.id }) {

    /** The `new` row overlays a NEW EPISODE strip on every poster (PRD §6.1). */
    var showNewStrip: Boolean = false

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemPosterBinding =
        ItemPosterBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemPosterBinding, item: Title, position: Int) {
        binding.root.bind(item)
        binding.newStrip.isVisible = showNewStrip
        binding.root.onSingleClick { callbacks.onOpenTitle(item.id) }
        // Long-press to save, as well as the hero and Title detail (PRD §6.6).
        binding.root.setOnLongClickListener {
            callbacks.onToggleMyList(item.id)
            true
        }
    }
}

// ---------------------------------------------------------------- Ranked row (Top 10)

/**
 * A Top 10 row's posters, each over its rank numeral.
 *
 * Rank is `position + 1`: the row arrives in rank order, so nothing has to be sorted or
 * scored here.
 */
private class RankedPosterAdapter(
    private val callbacks: HomeCallbacks,
) : BaseListAdapter<Title, ItemPosterRankedBinding>(diffBy { it.id }) {

    /**
     * Distinct from the plain poster's type because every row on Home shares one
     * [RecyclerView.RecycledViewPool], and the pool is keyed by view type alone. Leaving
     * this at the default 0 would let a plain row be handed a ranked view, and the holder
     * cast in [BaseListAdapter] would throw.
     */
    override fun getItemViewType(position: Int): Int = VIEW_TYPE

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemPosterRankedBinding =
        ItemPosterRankedBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemPosterRankedBinding, item: Title, position: Int) {
        val rank = position + 1
        binding.rankNumeral.rank = rank
        binding.poster.bind(item)

        // One announcement for the slot, carrying the rank, rather than the poster's own
        // description plus a separate numeral.
        binding.root.contentDescription =
            binding.root.resources.getString(R.string.cd_rank_poster, rank, item.title)

        binding.root.onSingleClick { callbacks.onOpenTitle(item.id) }
        binding.root.setOnLongClickListener {
            callbacks.onToggleMyList(item.id)
            true
        }
    }

    private companion object {
        /** Any value the plain poster adapter does not use. */
        const val VIEW_TYPE = 1
    }
}

