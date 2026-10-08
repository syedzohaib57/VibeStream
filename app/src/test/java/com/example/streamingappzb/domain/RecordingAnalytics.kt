package com.example.streamingappzb.domain

import com.example.streamingappzb.data.analytics.Analytics

/** Records the §10 events as `name{k=v}` strings, the same shape the logcat impl prints. */
class RecordingAnalytics : Analytics {

    val events = mutableListOf<String>()

    val names: List<String> get() = events.map { it.substringBefore('{') }

    fun clear() = events.clear()

    private fun log(name: String, vararg pairs: Pair<String, Any?>) {
        events += if (pairs.isEmpty()) {
            name
        } else {
            pairs.joinToString(", ", prefix = "$name{", postfix = "}") { "${it.first}=${it.second}" }
        }
    }

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
        log("search", "len" to queryLength, "results" to results)

    override fun myListToggle(titleId: Int, on: Boolean) =
        log("mylist_toggle", "id" to titleId, "on" to on)

    override fun dataSheetOpen() = log("data_sheet_open")
    override fun saverToggle(on: Boolean) = log("saver_toggle", "on" to on)
}
