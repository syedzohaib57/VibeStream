package com.example.streamingappzb.ui.title

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ItemEpisodeBinding
import com.example.streamingappzb.databinding.ItemEpisodesHeaderBinding
import com.example.streamingappzb.databinding.ItemTitleHeaderBinding
import com.example.streamingappzb.domain.format.Format
import com.example.streamingappzb.domain.model.DlState
import com.example.streamingappzb.domain.model.DownloadItem
import com.example.streamingappzb.domain.model.Episode
import com.example.streamingappzb.domain.model.Progress
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.model.Title
import com.example.streamingappzb.domain.model.megabytesFor
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.dp
import com.example.streamingappzb.ui.base.onSingleClick

sealed interface TitleItem {
    data class Header(
        val title: Title,
        val progress: Progress?,
        val inMyList: Boolean,
    ) : TitleItem

    data object EpisodesHeader : TitleItem

    data class EpisodeRow(
        val title: Title,
        val episode: Episode,
        val download: DownloadItem?,
        val watchedFraction: Float,
        val resumePositionSeconds: Int,
    ) : TitleItem
}

class TitleCallbacks(
    val onPrimaryPlay: () -> Unit,
    val onToggleMyList: () -> Unit,
    val onDownloadTitle: () -> Unit,
    val onPlayEpisode: (Int, Int) -> Unit,
    val onEpisodeGlyph: (Int) -> Unit,
)

class TitleAdapter(
    private val callbacks: TitleCallbacks,
    /** The rung to price an undownloaded episode at — the sheet's own default. */
    private val estimateRung: Rung,
) : ListAdapter<TitleItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TitleItem.Header -> TYPE_HEADER
        TitleItem.EpisodesHeader -> TYPE_EPISODES_HEADER
        is TitleItem.EpisodeRow -> TYPE_EPISODE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(
                ItemTitleHeaderBinding.inflate(inflater, parent, false),
                callbacks,
            )

            TYPE_EPISODES_HEADER -> SimpleHolder(
                ItemEpisodesHeaderBinding.inflate(inflater, parent, false).root,
            )

            else -> EpisodeHolder(
                ItemEpisodeBinding.inflate(inflater, parent, false),
                callbacks,
                estimateRung,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TitleItem.Header -> (holder as HeaderHolder).bind(item)
            is TitleItem.EpisodeRow -> (holder as EpisodeHolder).bind(item)
            TitleItem.EpisodesHeader -> Unit
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EPISODES_HEADER = 1
        const val TYPE_EPISODE = 2

        val DIFF = object : DiffUtil.ItemCallback<TitleItem>() {
            override fun areItemsTheSame(oldItem: TitleItem, newItem: TitleItem): Boolean = when {
                oldItem is TitleItem.Header && newItem is TitleItem.Header -> true
                oldItem is TitleItem.EpisodesHeader && newItem is TitleItem.EpisodesHeader -> true
                oldItem is TitleItem.EpisodeRow && newItem is TitleItem.EpisodeRow ->
                    oldItem.episode.number == newItem.episode.number

                else -> false
            }

            override fun areContentsTheSame(oldItem: TitleItem, newItem: TitleItem): Boolean =
                oldItem == newItem
        }
    }
}

private class SimpleHolder(view: android.view.View) : RecyclerView.ViewHolder(view)

// ---------------------------------------------------------------- Header

private class HeaderHolder(
    private val binding: ItemTitleHeaderBinding,
    private val callbacks: TitleCallbacks,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TitleItem.Header) {
        val title = item.title
        val res = binding.root.resources

        binding.backdrop.bind(title, showLabel = false)
        binding.rosette.isVisible = title.artUrl == null
        binding.title.text = title.title

        val runtime = if (title.isFilm) {
            val (hours, minutes) = Format.runtimeParts(title.runtimeMinutes ?: 0)
            res.getString(R.string.runtime_h_m, hours, minutes)
        } else {
            res.getQuantityString(R.plurals.episodes_count, title.totalEpisodes, title.totalEpisodes)
        }
        binding.meta.text = listOf(title.year.toString(), runtime, title.genre)
            .joinToString(META_SEPARATOR)

        // The rights record decides whether downloading exists at all here.
        binding.downloadableChip.isVisible = title.downloadable
        binding.streamOnly.isVisible = !title.downloadable
        binding.btnDownload.isVisible = title.downloadable
        binding.btnDownload.setText(
            if (title.isFilm) R.string.action_download else R.string.action_download_season,
        )
        binding.btnDownload.onSingleClick { callbacks.onDownloadTitle() }

        // Resume · Ep N · mm:ss / Resume · mm:ss / Play Episode 1 / Play (PRD §6.2)
        val progress = item.progress
        binding.btnPrimary.text = when {
            progress != null && title.isFilm ->
                res.getString(R.string.resume_at, Format.time(progress.positionSeconds))

            progress != null -> res.getString(
                R.string.resume_episode_at,
                progress.episode,
                Format.time(progress.positionSeconds),
            )

            title.isFilm -> res.getString(R.string.action_play)
            else -> res.getString(R.string.play_episode_one)
        }
        binding.btnPrimary.onSingleClick { callbacks.onPrimaryPlay() }

        binding.btnMyList.setIconResource(
            if (item.inMyList) R.drawable.ic_check else R.drawable.ic_add,
        )
        binding.btnMyList.contentDescription = res.getString(
            if (item.inMyList) R.string.cd_remove_from_list else R.string.cd_add_to_list,
        )
        binding.btnMyList.onSingleClick { callbacks.onToggleMyList() }

        binding.synopsis.text = title.synopsis
        binding.cast.isVisible = title.cast.isNotEmpty()
        if (title.cast.isNotEmpty()) {
            binding.cast.text = res.getString(R.string.with_cast, title.cast.joinToString(", "))
        }
    }

    private companion object {
        const val META_SEPARATOR = " · "
    }
}

// ---------------------------------------------------------------- Episode row

private class EpisodeHolder(
    private val binding: ItemEpisodeBinding,
    private val callbacks: TitleCallbacks,
    private val estimateRung: Rung,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TitleItem.EpisodeRow) {
        val res = binding.root.resources
        val context = binding.root.context
        val episode = item.episode

        binding.thumb.bind(item.title, showLabel = false)
        binding.epBadge.text = episode.number.toString()
        binding.epName.text = episode.name

        val watched = item.watchedFraction
        binding.epProgress.isVisible = watched > 0f
        binding.epProgress.progress = Format.percent(watched)

        binding.epMeta.text = buildMeta(res, item)

        binding.root.onSingleClick {
            callbacks.onPlayEpisode(episode.number, item.resumePositionSeconds)
        }
        binding.downloadSlot.onSingleClick { callbacks.onEpisodeGlyph(episode.number) }
        binding.downloadSlot.contentDescription =
            res.getString(R.string.cd_download_episode, episode.number)

        applyDownloadGlyph(item)
    }

    /** `45 min · 116 MB` plus the download state, always as words (PRD §8). */
    private fun buildMeta(res: android.content.res.Resources, item: TitleItem.EpisodeRow): String {
        val minutes = Format.minutesFromSeconds(item.episode.durationSeconds)
        val download = item.download
        val megabytes = download?.megabytes
            ?: estimateRung.megabytesFor(item.episode.durationSeconds)

        val head = buildString {
            append(res.getString(R.string.minutes_short, minutes))
            if (item.title.downloadable) {
                append(" · ")
                append(Format.megabytes(megabytes))
            }
        }

        val suffix = when (download?.state) {
            DlState.Done -> " " + res.getString(R.string.dot_downloaded)
            DlState.Waiting -> " " + res.getString(R.string.dot_waiting_wifi)
            DlState.Downloading -> " " + res.getString(R.string.dot_percent, download.percent)
            DlState.Queued -> " · " + res.getString(R.string.status_queued)
            DlState.Paused -> " · " + res.getString(R.string.status_paused)
            DlState.Failed -> " · " + res.getString(R.string.action_retry)
            null -> ""
        }
        return head + suffix
    }

    /**
     * The design's `DownloadGlyph`: lock when stream-only, `download_for_offline` when
     * absent, a progress ring while downloading, `schedule` when queued, `wifi` while
     * parked, and a cyan tick when done.
     */
    private fun applyDownloadGlyph(item: TitleItem.EpisodeRow) {
        val context = binding.root.context
        val ring = binding.dlRing
        val glyph = binding.dlGlyph

        fun glyphOnly(iconRes: Int, colourRes: Int, sizeDp: Int = 26) {
            ring.isVisible = false
            glyph.isVisible = true
            glyph.setImageResource(iconRes)
            glyph.imageTintList = ColorStateList.valueOf(context.color(colourRes))
            glyph.updateSize(context.dp(sizeDp))
        }

        when {
            !item.title.downloadable ->
                glyphOnly(R.drawable.ic_lock, R.color.text_low, sizeDp = 20)

            item.download == null ->
                glyphOnly(R.drawable.ic_download_for_offline, R.color.text_mid)

            item.download.state == DlState.Done ->
                glyphOnly(R.drawable.ic_download_done, R.color.cyan400, sizeDp = 24)

            item.download.state == DlState.Waiting ->
                glyphOnly(R.drawable.ic_wifi, R.color.cyan400, sizeDp = 22)

            item.download.state == DlState.Queued ->
                glyphOnly(R.drawable.ic_schedule, R.color.text_mid, sizeDp = 22)

            item.download.state == DlState.Failed ->
                glyphOnly(R.drawable.ic_error, R.color.rose400, sizeDp = 22)

            else -> {
                // Downloading or paused: the ring carries the percentage, with a small
                // stop/resume glyph inside it.
                ring.isVisible = true
                ring.percent = item.download.percent
                glyph.isVisible = true
                glyph.setImageResource(
                    if (item.download.state == DlState.Paused) {
                        R.drawable.ic_play_arrow_fill
                    } else {
                        R.drawable.ic_stop
                    },
                )
                glyph.imageTintList = ColorStateList.valueOf(context.color(R.color.cyan400))
                glyph.updateSize(context.dp(12))
            }
        }
    }

    private fun android.widget.ImageView.updateSize(sizePx: Int) {
        val params = layoutParams as FrameLayout.LayoutParams
        if (params.width != sizePx) {
            params.width = sizePx
            params.height = sizePx
            layoutParams = params
        }
    }
}
