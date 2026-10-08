package com.example.streamingappzb.ui.media

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.example.streamingappzb.R
import com.example.streamingappzb.domain.model.MediaItem
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Display strings for real catalogue items. All user-visible text comes from resources. */
object MediaFormat {

    fun typeLabel(context: Context, type: MediaType): String = context.getString(
        when (type) {
            MediaType.Movie -> R.string.type_film
            MediaType.Tv -> R.string.type_series
            MediaType.Anime -> R.string.type_anime
        },
    )

    /** "2024 · Film" under a poster. */
    fun posterCaption(context: Context, item: MediaItem): String =
        listOfNotNull(item.year?.toString(), typeLabel(context, item.type))
            .joinToString(" · ")

    /**
     * "COMING 12 MAR" for an unreleased title, or null.
     *
     * Dated rather than a bare "coming soon": the date is the whole reason someone looks
     * at an upcoming row, and it is the field TMDB is most reliable about.
     */
    fun comingLabel(context: Context, item: MediaItem): String? {
        val date = item.releaseDate ?: return null
        if (!item.isUpcoming(todayIso())) return null
        val pretty = shortDate(date) ?: return context.getString(R.string.coming_soon_short)
        return context.getString(R.string.coming_on, pretty).uppercase(Locale.getDefault())
    }

    /** One announcement per poster: title, type, year, rating, release. */
    fun contentDescription(context: Context, item: MediaItem): String = buildString {
        append(item.title)
        append(", ")
        append(typeLabel(context, item.type))
        item.year?.let {
            append(", ")
            append(it)
        }
        item.ratingOutOfTen?.let {
            append(", ")
            append(context.getString(R.string.cd_rated_out_of_ten, it))
        }
        comingLabel(context, item)?.let {
            append(", ")
            append(it)
        }
    }

    /**
     * Builds the hero's metadata row: year, type, rating, and a "COMING" chip when it has
     * not been released. Items are added as siblings so the row can scroll rather than
     * wrap at a large font scale.
     */
    fun fillHeroMeta(row: LinearLayout, item: MediaItem) {
        val context = row.context
        row.removeAllViews()

        item.year?.let { row.addMetaText(it.toString()) }
        row.addMetaText(typeLabel(context, item.type))
        item.ratingOutOfTen?.let { row.addMetaBadge(context.getString(R.string.rating_star, it)) }
        comingLabel(context, item)?.let { row.addMetaBadge(it, accent = true) }
    }

    private fun LinearLayout.addMetaText(text: String) {
        addView(
            TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Num_Meta)
                this.text = text
                setPadding(0, 0, context.dimen(R.dimen.gap_button_tight), 0)
            },
        )
    }

    private fun LinearLayout.addMetaBadge(text: String, accent: Boolean = false) {
        addView(
            TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_Mh_Num_Micro)
                this.text = text
                gravity = Gravity.CENTER
                setTextColor(context.color(if (accent) R.color.rose400 else R.color.ember400))
                setBackgroundResource(
                    if (accent) R.drawable.bg_badge_rose else R.drawable.bg_rating_badge,
                )
                val h = context.dimen(R.dimen.gap_button_tight)
                setPadding(h, h / 2, h, h / 2)
            },
        )
        addView(
            android.view.View(context),
            LinearLayout.LayoutParams(context.dimen(R.dimen.gap_button_tight), 1),
        )
    }

    /** "12 Mar 2026" from `yyyy-MM-dd`, or null when the date is unparseable. */
    fun shortDate(iso: String): String? = runCatching {
        val parsed = SimpleDateFormat(ISO, Locale.ROOT).parse(iso) ?: return null
        SimpleDateFormat(PRETTY, Locale.getDefault()).format(parsed)
    }.getOrNull()

    /** Runtime as "1h 52m" / "48m", matching the rest of the app. */
    fun runtime(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
    }

    private fun todayIso(): String = with(Calendar.getInstance()) {
        "%04d-%02d-%02d".format(
            get(Calendar.YEAR),
            get(Calendar.MONTH) + 1,
            get(Calendar.DAY_OF_MONTH),
        )
    }

    private const val ISO = "yyyy-MM-dd"
    private const val PRETTY = "d MMM"
}
