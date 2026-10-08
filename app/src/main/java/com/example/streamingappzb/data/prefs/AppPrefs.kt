package com.example.streamingappzb.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.Locale

/**
 * The app's SharedPreferences, in one place.
 *
 * Thin on purpose: reads and writes only, no rules. [com.example.streamingappzb.data
 * .prefs.SettingsRepositoryImpl] wraps this to expose flows and enforce the specified
 * defaults.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---------- Data ----------

    /** Data Saver. On by default (PRD §6.7). */
    var saver: Boolean
        get() = prefs.getBoolean(KEY_SAVER, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVER, value).apply()

    /** Download on Wi-Fi only. On by default (PRD §6.5). */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** Smart downloads. Off by default (PRD §6.5). */
    var smartDownloads: Boolean
        get() = prefs.getBoolean(KEY_SMART, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART, value).apply()

    // ---------- Playback ----------

    var subtitles: String
        get() = prefs.getString(KEY_SUBS, DEFAULT_SUBS) ?: DEFAULT_SUBS
        set(value) = prefs.edit().putString(KEY_SUBS, value).apply()

    /** A hand-picked rung. Cleared when the network or the Saver setting changes. */
    var manualQuality: String?
        get() = prefs.getString(KEY_MANUAL_QUALITY, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_MANUAL_QUALITY) else putString(KEY_MANUAL_QUALITY, value)
        }.apply()

    var language: String
        get() = prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    // ---------- Watch region ----------

    /**
     * The viewer's chosen country for watch-provider lookups, or null to follow the
     * device. Stored separately from [language] because they are genuinely different
     * questions — what you read and where you can watch.
     */
    fun region(): String? = prefs.getString(KEY_REGION, null)

    fun setRegion(code: String?) = prefs.edit().apply {
        if (code == null) remove(KEY_REGION) else putString(KEY_REGION, code)
    }.apply()

    // ---------- Personal library ----------

    /**
     * The SAF tree URIs the viewer has pointed the app at, as strings.
     *
     * Only the URI is stored. The *contents* are re-scanned on demand rather than
     * mirrored into Room, because a folder the viewer manages themselves changes without
     * telling us and a stale index would offer a play button for a file that has been
     * moved.
     *
     * Copied on the way out: `getStringSet` hands back a set that SharedPreferences owns
     * and documents as unsafe to mutate or retain.
     */
    var libraryFolders: Set<String>
        get() = prefs.getStringSet(KEY_LIBRARY_FOLDERS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LIBRARY_FOLDERS, value.toSet()).apply()

    /**
     * Search the whole Internet Archive rather than its curated film collections, and
     * without the pre-1930 ceiling.
     *
     * On by default in this build, which is a personal one: it finds far more, at the cost
     * of sometimes matching an unverified upload — so the attribution shown next to the
     * play button says exactly that when this is on.
     */
    var openArchiveSearch: Boolean
        get() = prefs.getBoolean(KEY_OPEN_ARCHIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_ARCHIVE, value).apply()

    /**
     * Fall back to a bundled sample stream when nothing else resolves, so the play button
     * never dead-ends.
     *
     * Off by default, and labelled as a sample wherever it appears. It plays the full
     * player pipeline — rungs, ad breaks, Up next, the seek rail — against a title that
     * has no real source, which is the only way to exercise any of that on the catalogue
     * at large. It is a development aid, not a way to watch the film.
     */
    var sampleFallback: Boolean
        get() = prefs.getBoolean(KEY_SAMPLE_FALLBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLE_FALLBACK, value).apply()

    // ---------- Jellyfin ----------

    /**
     * The connected Jellyfin server, or nulls when there is none.
     *
     * The token is Jellyfin's own long-lived access token, stored exactly as issued. The
     * password is never stored — it is exchanged for the token once, in the connect
     * dialog, and discarded.
     */
    var jellyfinUrl: String?
        get() = prefs.getString(KEY_JF_URL, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_JF_URL) else putString(KEY_JF_URL, value)
        }.apply()

    var jellyfinToken: String?
        get() = prefs.getString(KEY_JF_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_JF_TOKEN) else putString(KEY_JF_TOKEN, value)
        }.apply()

    var jellyfinUserName: String?
        get() = prefs.getString(KEY_JF_USER, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_JF_USER) else putString(KEY_JF_USER, value)
        }.apply()

    // ---------- First run / one-shot flags ----------

    fun isSeeded(): Boolean = prefs.getBoolean(KEY_SEEDED, false)

    fun markSeeded() = prefs.edit().putBoolean(KEY_SEEDED, true).apply()

    fun hasAskedNotifications(): Boolean = prefs.getBoolean(KEY_ASKED_NOTIFS, false)

    fun markAskedNotifications() = prefs.edit().putBoolean(KEY_ASKED_NOTIFS, true).apply()

    // ---------- Month-to-date data counters ----------

    /**
     * Bytes streamed over metered and unmetered transports this calendar month, behind the
     * Data sheet's "This month" line. Rolls over automatically when the month changes.
     */
    val streamedBytes: Long get() = withCurrentMonth { prefs.getLong(KEY_BYTES_METERED, 0L) }

    val wifiBytes: Long get() = withCurrentMonth { prefs.getLong(KEY_BYTES_UNMETERED, 0L) }

    fun addBytes(bytes: Long, metered: Boolean) {
        if (bytes <= 0) return
        val key = if (metered) KEY_BYTES_METERED else KEY_BYTES_UNMETERED
        val current = withCurrentMonth { prefs.getLong(key, 0L) }
        prefs.edit().putLong(key, current + bytes).apply()
    }

    /** Resets the counters the first time they are touched in a new month. */
    private fun <T> withCurrentMonth(read: () -> T): T {
        val month = currentMonthKey()
        if (prefs.getString(KEY_MONTH, null) != month) {
            prefs.edit()
                .putString(KEY_MONTH, month)
                .putLong(KEY_BYTES_METERED, 0L)
                .putLong(KEY_BYTES_UNMETERED, 0L)
                .apply()
        }
        return read()
    }

    private fun currentMonthKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
        )
    }

    private companion object {
        const val NAME = "moviehub_prefs"

        const val KEY_SAVER = "saver"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_SMART = "smart_downloads"
        const val KEY_SUBS = "subtitles"
        const val KEY_MANUAL_QUALITY = "manual_quality"
        const val KEY_LANG = "language"
        const val KEY_REGION = "watch_region"
        const val KEY_LIBRARY_FOLDERS = "library_folders"
        const val KEY_OPEN_ARCHIVE = "open_archive_search"
        const val KEY_SAMPLE_FALLBACK = "sample_fallback"
        const val KEY_JF_URL = "jellyfin_url"
        const val KEY_JF_TOKEN = "jellyfin_token"
        const val KEY_JF_USER = "jellyfin_user"
        const val KEY_SEEDED = "seeded"
        const val KEY_ASKED_NOTIFS = "asked_notifications"
        const val KEY_MONTH = "counter_month"
        const val KEY_BYTES_METERED = "bytes_metered"
        const val KEY_BYTES_UNMETERED = "bytes_unmetered"

        const val DEFAULT_SUBS = "English"
        const val DEFAULT_LANG = "en"
    }
}
