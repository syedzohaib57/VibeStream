package com.example.streamingappzb.domain.model

/**
 * What a title is. Drives the hero metadata row, the episode list, and whether
 * "New episodes" may be shown at all (PRD §6.1 — never on a Film).
 */
enum class Kind {
    Drama,
    Film,
    Documentary,
    ;

    val isSeries: Boolean get() = this != Film

    companion object {
        /** Tolerant of an unknown value from a future server response. */
        fun from(raw: String?): Kind = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Drama
    }
}

/**
 * The live connection, from [com.example.streamingappzb.data.network.NetworkMonitor].
 * Everything data-related in the app keys off this: the app-bar pill, the quality
 * default (FR-204), and whether a download may start (FR-303).
 */
enum class NetworkState {
    /** Unmetered. Streams default to 720p, downloads run freely. */
    Wifi,

    /** Metered. Streams default to 240p with Saver on, 480p with it off. */
    Cellular,

    /** No transport. Downloads still play; network-backed rows collapse. */
    Offline,
    ;

    val isMetered: Boolean get() = this == Cellular
    val isOnline: Boolean get() = this != Offline
}

/**
 * Download lifecycle. [Waiting] is ours rather than Media3's — it is the state an item
 * sits in when the user chose "Queue for Wi-Fi" on mobile data (FR-303), and it becomes
 * [Queued] the moment the network turns unmetered.
 */
enum class DlState {
    Queued,
    Waiting,
    Downloading,
    Paused,
    Done,
    Failed,
    ;

    val isActive: Boolean get() = this != Done
    val isRunning: Boolean get() = this == Downloading
}

/** Subtitle options. FR-207 ships exactly these two. */
enum class SubtitleOption(val tag: String?) {
    Off(null),
    English("en"),
    ;

    companion object {
        fun from(raw: String?): SubtitleOption =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: English
    }
}

/** How a stream is delivered. Picks the Media3 MediaSource factory. */
enum class StreamType {
    Hls,
    Dash,
    Progressive,
    ;

    companion object {
        fun from(raw: String?): StreamType = when (raw?.lowercase()) {
            "dash", "mpd" -> Dash
            "progressive", "mp4" -> Progressive
            else -> Hls
        }
    }
}
