package com.example.streamingappzb.ui.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class BindingViewHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)

/**
 * The base every row adapter extends: [ListAdapter] for diffing plus generic view
 * binding, so a row adapter is an [inflate] and a [bind] and nothing else.
 */
abstract class BaseListAdapter<T : Any, VB : ViewBinding>(
    diff: DiffUtil.ItemCallback<T>,
) : ListAdapter<T, BindingViewHolder<VB>>(diff) {

    protected abstract fun inflate(inflater: LayoutInflater, parent: ViewGroup): VB

    protected abstract fun bind(binding: VB, item: T, position: Int)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<VB> =
        BindingViewHolder(inflate(LayoutInflater.from(parent.context), parent))

    override fun onBindViewHolder(holder: BindingViewHolder<VB>, position: Int) {
        bind(holder.binding, getItem(position), position)
    }
}

/**
 * Identity-based diffing for items with a stable id.
 *
 * @param id extracts the stable identity
 */
inline fun <T : Any> diffBy(crossinline id: (T) -> Any): DiffUtil.ItemCallback<T> =
    object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = id(oldItem) == id(newItem)

        // Every model this is used with is a Kotlin data class, so `==` is a real
        // structural comparison. Lint cannot prove that through the type parameter.
        @Suppress("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
    }
