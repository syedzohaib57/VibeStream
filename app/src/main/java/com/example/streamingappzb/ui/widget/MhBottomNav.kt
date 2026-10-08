package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.streamingappzb.R
import com.example.streamingappzb.databinding.ViewBottomNavBinding
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.base.isVisibleOrGone
import com.example.streamingappzb.ui.base.onSingleClick
import com.example.streamingappzb.ui.base.padBottomForNavigationBar
import com.example.streamingappzb.ui.base.tint

/** The four tab routes (PRD §4). */
enum class MhTab(val route: String) {
    Home("home"),
    Search("search"),
    Downloads("downloads"),
    MyList("list"),
}

/**
 * Bottom nav.
 *
 * Shown on the four tab routes only — Title and Player are separate Activities, so that
 * rule is structural rather than something each screen has to remember (PRD §4).
 *
 * The 1dp top hairline at 6% white is drawn rather than layered, so it stays exactly one
 * physical pixel-rounded dp regardless of the navigation-bar inset padding below it.
 */
class MhBottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBottomNavBinding.inflate(LayoutInflater.from(context), this)

    private val borderPaint = Paint().apply {
        color = context.color(R.color.bottom_nav_border)
        strokeWidth = context.dimen(R.dimen.bottom_nav_border).toFloat()
    }

    private val activeColour = context.color(R.color.ember400)
    private val inactiveColour = context.color(R.color.text_mid)

    var onTabSelected: ((MhTab) -> Unit)? = null

    var selected: MhTab = MhTab.Home
        set(value) {
            field = value
            applySelection()
        }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(context.color(R.color.bottom_nav_bg))
        setWillNotDraw(false)
        minimumHeight = context.dimen(R.dimen.bottom_nav_height)
        // The nav pads by the navigation-bar inset (PRD §8, Edge to edge).
        padBottomForNavigationBar()

        binding.navHome.onSingleClick { select(MhTab.Home) }
        binding.navSearch.onSingleClick { select(MhTab.Search) }
        binding.navDownloads.onSingleClick { select(MhTab.Downloads) }
        binding.navList.onSingleClick { select(MhTab.MyList) }

        applySelection()
    }

    /** The cyan badge, and its count, from the active-download total. */
    fun setDownloadCount(count: Int) {
        binding.badgeDownloads.isVisibleOrGone = count > 0
        binding.badgeDownloads.text = count.toString()
        // Colour is never the only signal: the count reads out to TalkBack as well.
        binding.navDownloads.contentDescription = if (count > 0) {
            resources.getQuantityString(R.plurals.nav_downloads_active, count, count)
        } else {
            context.getString(R.string.nav_downloads)
        }
    }

    private fun select(tab: MhTab) {
        if (selected != tab) {
            selected = tab
        }
        onTabSelected?.invoke(tab)
    }

    private fun applySelection() {
        apply(binding.iconHome, binding.labelHome, MhTab.Home, R.drawable.ic_home, R.drawable.ic_home_fill)
        apply(binding.iconSearch, binding.labelSearch, MhTab.Search, R.drawable.ic_search, R.drawable.ic_search_fill)
        apply(binding.iconDownloads, binding.labelDownloads, MhTab.Downloads, R.drawable.ic_download, R.drawable.ic_download_fill)
        apply(binding.iconList, binding.labelList, MhTab.MyList, R.drawable.ic_bookmark, R.drawable.ic_bookmark_fill)
    }

    private fun apply(
        icon: ImageView,
        label: TextView,
        tab: MhTab,
        outlineRes: Int,
        filledRes: Int,
    ) {
        val on = tab == selected
        // The filled glyph plus the ember tint, matching `fill={on}` in the design.
        icon.setImageResource(if (on) filledRes else outlineRes)
        icon.tint(if (on) activeColour else inactiveColour)
        label.setTextColor(if (on) activeColour else inactiveColour)
        val item = icon.parent as? android.view.View
        (item?.parent as? android.view.View)?.isSelected = on
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = borderPaint.strokeWidth / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, borderPaint)
    }
}
