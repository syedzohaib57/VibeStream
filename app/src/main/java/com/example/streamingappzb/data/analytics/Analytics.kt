package com.example.streamingappzb.data.analytics

import android.util.Log

/**
 * The PRD §10 event set, as an explicit interface rather than a string-keyed bag so a
 * renamed parameter is a compile error.
 *
 * v1 ships [LogcatAnalytics]: no third-party SDK, so nothing leaves the device and there
 * is nothing extra to declare in Play Data Safety. Swapping in a real sink is one Koin
 * binding.
 */
interface Analytics {
    fun homeView(kind: String)
    fun chipSelect(kind: String)
    fun heroPlay(titleId: Int, resume: Boolean)
    fun titleView(titleId: Int)
    fun playStart(titleId: Int, episode: Int, rung: String, network: String)
    fun qualityChange(from: String, to: String, network: String, saver: Boolean)
    fun adStart(type: String)
    fun adSkip(elapsedSeconds: Int)
    fun upNextShown()
    fun upNextCancel()
    fun downloadRequest(titleId: Int, episode: Int, rung: String, megabytes: Int, queuedForWifi: Boolean)
    fun downloadComplete(titleId: Int, episode: Int)
    fun search(queryLength: Int, results: Int)
    fun myListToggle(titleId: Int, on: Boolean)
    fun dataSheetOpen()
    fun saverToggle(on: Boolean)
}

class LogcatAnalytics : Analytics {

    override fun homeView(kind: String) = log("home_view", "kind" to kind)

    override fun chipSelect(kind: String) = log("chip_select", "kind" to kind)

    override fun heroPlay(titleId: Int, resume: Boolean) =
        log("hero_play", "id" to titleId, "resume" to resume)

    override fun titleView(titleId: Int) = log("title_view", "id" to titleId)

    override fun playStart(titleId: Int, episode: Int, rung: String, network: String) =
        log("play_start", "id" to titleId, "ep" to episode, "rung" to rung, "network" to network)

    override fun qualityChange(from: String, to: String, network: String, saver: Boolean) =
        log("quality_change", "from" to from, "to" to to, "network" to network, "saver" to saver)

    override fun adStart(type: String) = log("ad_start", "type" to type)

    override fun adSkip(elapsedSeconds: Int) = log("ad_skip", "elapsed" to elapsedSeconds)

    override fun upNextShown() = log("upnext_shown")

    override fun upNextCancel() = log("upnext_cancel")

    override fun downloadRequest(
        titleId: Int,
        episode: Int,
        rung: String,
        megabytes: Int,
        queuedForWifi: Boolean,
    ) = log(
        "download_request",
        "id" to titleId,
        "ep" to episode,
        "rung" to rung,
        "mb" to megabytes,
        "queued_for_wifi" to queuedForWifi,
    )

    override fun downloadComplete(titleId: Int, episode: Int) =
        log("download_complete", "id" to titleId, "ep" to episode)

    override fun search(queryLength: Int, results: Int) =
        log("search", "q_len" to queryLength, "results" to results)

    override fun myListToggle(titleId: Int, on: Boolean) =
        log("mylist_toggle", "id" to titleId, "on" to on)

    override fun dataSheetOpen() = log("data_sheet_open")

    override fun saverToggle(on: Boolean) = log("saver_toggle", "on" to on)

    private fun log(name: String, vararg params: Pair<String, Any?>) {
        if (params.isEmpty()) {
            Log.d(TAG, name)
        } else {
            Log.d(TAG, params.joinToString(", ", prefix = "$name{", postfix = "}") { "${it.first}=${it.second}" })
        }
    }

    private companion object {
        const val TAG = "MhAnalytics"
    }
}
