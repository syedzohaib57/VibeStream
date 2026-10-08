package com.example.streamingappzb.domain.model

/**
 * One step on the bitrate ladder (PRD §6.7). Tuned for data cost, not picture quality —
 * [mbPerHour] is the number the viewer actually sees, everywhere.
 *
 * @param id          the label shown to the user, e.g. "240p"
 * @param subKey      name of the strings.xml entry for the one-line explanation
 * @param mbPerHour   megabytes of mobile data an hour of this rung costs
 * @param maxHeight   pixel height, fed to TrackSelectionParameters.setMaxVideoSize
 */
data class Rung(
    val id: String,
    val subKey: String,
    val mbPerHour: Int,
    val maxHeight: Int,
) {
    /**
     * Bitrate ceiling implied by [mbPerHour], for setMaxVideoBitrate. MB here is 10^6
     * bytes, the unit carriers and CDNs bill in — so 155 MB/hr is ~344 kbps, and
     * 1050 MB/hr is ~2.3 Mbps.
     */
    val maxBitrateBps: Int get() = ((mbPerHour * BYTES_PER_MB * BITS_PER_BYTE) / SECONDS_PER_HOUR).toInt()

    /** 16:9 width for the height, so setMaxVideoSize gets a sane pair. */
    val maxWidth: Int get() = (maxHeight * 16) / 9

    /** Above the Data Saver ceiling, so locked on mobile data while Saver is on (FR-204). */
    val isAboveSaverCap: Boolean get() = mbPerHour > SAVER_CAP_MB_PER_HOUR

    companion object {
        /** Data Saver caps mobile-data streams at 360p, i.e. 300 MB/hr (PRD §6.7). */
        const val SAVER_CAP_MB_PER_HOUR = 300

        const val ID_144P = "144p"
        const val ID_240P = "240p"
        const val ID_360P = "360p"
        const val ID_480P = "480p"
        const val ID_720P = "720p"
        const val ID_1080P = "1080p"

        /** Default on mobile data with Data Saver on. */
        const val DEFAULT_CELLULAR_SAVER = ID_240P

        /** Default on mobile data with Data Saver off. */
        const val DEFAULT_CELLULAR = ID_480P

        /** Default on Wi-Fi. */
        const val DEFAULT_WIFI = ID_720P

        /** Default rung offered in the download sheet (PRD §6.2). */
        const val DEFAULT_DOWNLOAD = ID_240P

        /** The download sheet offers 240p / 360p / 480p only (PRD §6.2). */
        val DOWNLOAD_RUNG_IDS = listOf(ID_240P, ID_360P, ID_480P)

        /**
         * The §6.7 ladder as specified. The catalogue may override it, but having it in
         * code means [com.example.streamingappzb.domain.repository.CatalogRepository.rungs]
         * can stay synchronous — the player and the quality sheet need a rung on the very
         * first frame, before any asset has been read.
         */
        val LADDER: List<Rung> = listOf(
            Rung(ID_144P, "rung_sub_144p", 80, 144),
            Rung(ID_240P, "rung_sub_240p", 155, 240),
            Rung(ID_360P, "rung_sub_360p", 300, 360),
            Rung(ID_480P, "rung_sub_480p", 540, 480),
            Rung(ID_720P, "rung_sub_720p", 1050, 720),
            Rung(ID_1080P, "rung_sub_1080p", 1850, 1080),
        )

        private const val BYTES_PER_MB = 1_000_000L
        private const val BITS_PER_BYTE = 8L
        private const val SECONDS_PER_HOUR = 3_600L
    }
}

/**
 * Megabytes an episode costs at a rung — shown *before* anything downloads (FR-302).
 * This is the same arithmetic the download sheet and the Downloads list both use, so
 * the estimate and the reported size can never drift apart.
 */
fun Rung.megabytesFor(durationSeconds: Int): Double =
    mbPerHour.toDouble() * durationSeconds / 3_600.0
