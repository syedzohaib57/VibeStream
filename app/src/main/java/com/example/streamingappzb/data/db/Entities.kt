package com.example.streamingappzb.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The catalogue, seeded from `assets/catalog.json` on first run. Home, Search and
 * My List all read from here, which is what lets them render with no network (PRD §8).
 *
 * List-shaped fields are stored joined rather than as separate tables: the catalogue is
 * read-only to the client and never queried by cast member or ad break.
 */
@Entity(tableName = "titles")
data class TitleEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val kind: String,
    val year: Int,
    val episodeCount: Int?,
    val runtimeMinutes: Int?,
    val genre: String,
    val synopsis: String,
    /** Newline-joined; no cast name contains a newline. */
    val cast: String,
    val c1: Int,
    val c2: Int,
    val motif: Int,
    val artUrl: String?,
    val downloadable: Boolean,
    val fresh: Boolean,
    /** Comma-joined 0..1 fractions of runtime. */
    val adBreaks: String,
    val streamUrl: String,
    val streamType: String,
    val hasSubtitles: Boolean,
    val drmScheme: String?,
    val drmLicenseUrl: String?,
    /** Newline-separated `name: value` pairs. Null for clear content. */
    val drmHeaders: String?,
    /** Editorial order from the catalogue, so lists are never alphabetised by accident. */
    val sortOrder: Int,
)

/**
 * A curated Home row. Stored so Home is genuinely DB-backed offline; in production these
 * arrive from `GET /home` and are cached here.
 */
@Entity(tableName = "home_rows")
data class HomeRowEntity(
    @PrimaryKey val key: String,
    val title: String,
    /** Comma-joined title ids, in editorial order. */
    val titleIds: String,
    val sortOrder: Int,
)

/** A genre browse tile (PRD §6.4). */
@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val name: String,
    val c1: Int,
    val motif: Int,
    val sortOrder: Int,
)

/**
 * Continue watching. One row per title — resuming a different episode of the same title
 * replaces it, which is why the title id is the key.
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val titleId: Int,
    val episode: Int,
    val positionSeconds: Int,
    val updatedAt: Long,
)

/** My List (PRD §6.6). [addedAt] gives the grid its most-recent-first order. */
@Entity(tableName = "my_list")
data class MyListEntity(
    @PrimaryKey val titleId: Int,
    val addedAt: Long,
)

/**
 * Our mirror of a Media3 download, carrying the two things Media3 does not model: the
 * rung the viewer chose with its pre-start MB estimate (FR-302), and [queuedForWifi],
 * which is what makes the explicit `waiting` state possible (FR-303).
 */
@Entity(
    tableName = "downloads",
    indices = [Index(value = ["titleId", "episode"], unique = true)],
)
data class DownloadEntity(
    /** "{titleId}-{episode}", matching Media3's download id for this item. */
    @PrimaryKey val key: String,
    val titleId: Int,
    val episode: Int,
    val state: String,
    val percent: Int,
    val rungId: String,
    val megabytes: Double,
    /** Licence window end. Null until the download completes. */
    val expiresAtMillis: Long?,
    /**
     * Parked by "Queue for Wi-Fi": the row exists and shows as `waiting`, but the item is
     * deliberately withheld from the Media3 queue so no global requirement change can
     * start it early (FR-303).
     */
    val queuedForWifi: Boolean,
    /**
     * The viewer explicitly chose "Use mobile data now" for this item. Persisted rather
     * than held in memory so the consent survives process death — otherwise a download
     * they asked to run on data would silently stall after a restart.
     */
    val allowMetered: Boolean = false,
    /** Widevine offline licence key set. Null for clear content. */
    val offlineLicenseKeySetId: ByteArray? = null,
    val createdAt: Long,
) {
    // ByteArray needs identity-free equals for Room's change detection to behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadEntity) return false
        return key == other.key &&
            titleId == other.titleId &&
            episode == other.episode &&
            state == other.state &&
            percent == other.percent &&
            rungId == other.rungId &&
            megabytes == other.megabytes &&
            expiresAtMillis == other.expiresAtMillis &&
            queuedForWifi == other.queuedForWifi &&
            allowMetered == other.allowMetered &&
            offlineLicenseKeySetId.contentEquals(other.offlineLicenseKeySetId) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + percent
        result = 31 * result + (expiresAtMillis?.hashCode() ?: 0)
        result = 31 * result + queuedForWifi.hashCode()
        result = 31 * result + allowMetered.hashCode()
        result = 31 * result + (offlineLicenseKeySetId?.contentHashCode() ?: 0)
        return result
    }
}
