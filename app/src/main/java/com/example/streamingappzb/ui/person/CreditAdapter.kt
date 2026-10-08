package com.example.streamingappzb.ui.person

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streamingappzb.databinding.ItemMediaPosterBinding
import com.example.streamingappzb.domain.model.Credit
import com.example.streamingappzb.ui.media.MediaFormat

/**
 * A filmography row.
 *
 * Reuses the discover poster cell, with the caption replaced by the *role* rather than the
 * year and type — on a person's page "Ellen Ripley" is the thing you are scanning for, and
 * the year is already implied by the date ordering.
 */
class CreditAdapter(
    private val onOpen: (Credit) -> Unit,
) : ListAdapter<Credit, CreditAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMediaPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onOpen,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemMediaPosterBinding,
        private val onOpen: (Credit) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(credit: Credit) = with(binding) {
            val item = credit.item
            poster.bind(item)

            // The role, falling back to the usual caption when TMDB records no role — an
            // uncredited or archive-footage entry, which does happen.
            caption.text = credit.role ?: MediaFormat.posterCaption(root.context, item)

            val score = item.ratingOutOfTen
            rating.isVisible = score != null
            rating.text = score

            val coming = MediaFormat.comingLabel(root.context, item)
            comingStrip.isVisible = coming != null
            comingStrip.text = coming

            root.setOnClickListener { onOpen(credit) }
            root.contentDescription = listOfNotNull(item.title, credit.role).joinToString(", ")
        }
    }

    private object Diff : DiffUtil.ItemCallback<Credit>() {
        override fun areItemsTheSame(old: Credit, new: Credit) = old.item.key == new.item.key

        override fun areContentsTheSame(old: Credit, new: Credit) = old == new
    }
}
