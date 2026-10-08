package com.example.streamingappzb.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Even gaps in a grid.
 *
 * Margins on the item would double up between columns and leave the outer edges
 * inconsistent with the screen gutter; a decoration keeps every gap exactly [spacing] and
 * every edge flush with the RecyclerView's own padding — which is what the 10dp gaps in
 * My List and the genre grid need (PRD §6.6 / §6.4).
 */
class GridSpacingDecoration(
    private val columns: Int,
    private val spacing: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view).takeIf { it >= 0 } ?: return
        val column = position % columns

        // Each item gets a share of the total horizontal gap proportional to its column,
        // so the columns stay equal width and the outer edges carry no extra inset.
        outRect.left = column * spacing / columns
        outRect.right = spacing - (column + 1) * spacing / columns
        if (position >= columns) outRect.top = spacing
    }
}
