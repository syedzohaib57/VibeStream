package com.example.streamingappzb.ui.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.streamingappzb.R
import com.example.streamingappzb.data.remote.tmdb.TmdbImages
import com.example.streamingappzb.databinding.ItemCastBinding
import com.example.streamingappzb.databinding.ItemMediaEpisodeBinding
import com.example.streamingappzb.domain.model.CastMember
import com.example.streamingappzb.domain.model.CrewMember
import com.example.streamingappzb.domain.model.EpisodeSummary

/**
 * The cast row.
 *
 * @param onOpen a face is tappable when TMDB gave it a person id, which opens their
 *   filmography. AniList characters carry no such id and are bound with `id = 0`, so they
 *   render identically but do nothing — a dead tap would be worse than no affordance.
 */
class CastAdapter(
    private val onOpen: (CastMember) -> Unit = {},
) : ListAdapter<CastMember, CastAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemCastBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onOpen,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemCastBinding,
        private val onOpen: (CastMember) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: CastMember) = with(binding) {
            name.text = member.name
            character.text = member.character.orEmpty()
            character.isVisible = !member.character.isNullOrBlank()

            val url = TmdbImages.profile(member.profilePath)
            if (url == null) {
                photo.setImageDrawable(null)
            } else {
                photo.load(url) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            }

            val openable = member.id > 0
            root.isClickable = openable
            root.setOnClickListener(if (openable) View.OnClickListener { onOpen(member) } else null)
            // One announcement for the pair, so TalkBack does not read two fragments.
            root.contentDescription = listOfNotNull(member.name, member.character)
                .joinToString(", ")
        }
    }

    private object Diff : DiffUtil.ItemCallback<CastMember>() {
        override fun areItemsTheSame(old: CastMember, new: CastMember) =
            old.name == new.name && old.character == new.character

        override fun areContentsTheSame(old: CastMember, new: CastMember) = old == new
    }
}

/**
 * The crew row — directors and writers, which the cast row does not cover.
 *
 * Reuses the cast cell: the shape is identical (a face, a name, a role) and the only
 * difference is that the second line is a job rather than a character.
 */
class CrewAdapter(
    private val onOpen: (CrewMember) -> Unit = {},
) : ListAdapter<CrewMember, CrewAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemCastBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onOpen,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemCastBinding,
        private val onOpen: (CrewMember) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: CrewMember) = with(binding) {
            name.text = member.name
            character.text = member.job
            character.isVisible = true

            val url = TmdbImages.profile(member.profilePath)
            if (url == null) {
                photo.setImageDrawable(null)
            } else {
                photo.load(url) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            }

            val openable = member.id > 0
            root.isClickable = openable
            root.setOnClickListener(if (openable) View.OnClickListener { onOpen(member) } else null)
            root.contentDescription = "${member.name}, ${member.job}"
        }
    }

    private object Diff : DiffUtil.ItemCallback<CrewMember>() {
        override fun areItemsTheSame(old: CrewMember, new: CrewMember) =
            old.id == new.id && old.job == new.job

        override fun areContentsTheSame(old: CrewMember, new: CrewMember) = old == new
    }
}

/**
 * Episodes for the selected season.
 *
 * Unaired episodes stay in the list, dimmed and labelled: hiding them makes it impossible
 * to tell how much of a season is left, which is one of the things a viewer opens a series
 * page to find out.
 */
class MediaEpisodeAdapter(
    private val todayIso: () -> String,
    /** A tap on an aired episode. Unaired rows stay inert — there is nothing to play. */
    private val onClick: (EpisodeSummary) -> Unit = {},
) : ListAdapter<EpisodeSummary, MediaEpisodeAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMediaEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position), todayIso())

    class Holder(
        private val binding: ItemMediaEpisodeBinding,
        private val onClick: (EpisodeSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: EpisodeSummary, today: String) = with(binding) {
            val context = root.context
            name.text = episode.name
            number.text = "E${episode.episodeNumber}"

            val aired = episode.hasAired(today)
            val date = episode.airDate?.let(MediaFormat::shortDate)
            val runtime = episode.runtimeMinutes?.let(MediaFormat::runtime)
            sub.text = when {
                !aired -> listOfNotNull(context.getString(R.string.not_yet_aired), date)
                    .joinToString(" · ")
                else -> listOfNotNull(date, runtime).joinToString(" · ")
            }

            // Dimmed *and* worded — colour is never the only signal.
            val alpha = if (aired) 1f else DIMMED
            still.alpha = alpha
            name.alpha = alpha

            val url = TmdbImages.still(episode.stillPath)
            if (url == null) {
                still.setImageDrawable(null)
            } else {
                still.load(url) { crossfade(true) }
            }

            root.contentDescription = "${episode.episodeNumber}. ${episode.name}. ${sub.text}"

            root.isClickable = aired
            root.setOnClickListener(if (aired) { _ -> onClick(episode) } else null)
        }

        private companion object {
            const val DIMMED = 0.45f
        }
    }

    private object Diff : DiffUtil.ItemCallback<EpisodeSummary>() {
        override fun areItemsTheSame(old: EpisodeSummary, new: EpisodeSummary) =
            old.seasonNumber == new.seasonNumber && old.episodeNumber == new.episodeNumber

        override fun areContentsTheSame(old: EpisodeSummary, new: EpisodeSummary) = old == new
    }
}
