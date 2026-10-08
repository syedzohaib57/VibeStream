package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.NetworkState
import com.example.streamingappzb.domain.model.Rung
import com.example.streamingappzb.domain.repository.CatalogRepository

/** One row of the quality sheet. */
data class QualityOption(
    val rung: Rung,
    val selected: Boolean,
    /**
     * Above the Data Saver ceiling on mobile data. Locked rows carry a lock glyph *and*
     * the words "Turn off Data Saver to use" — colour is never the only signal (PRD §8).
     */
    val locked: Boolean,
)

/**
 * The FR-204 quality rules, in one place.
 *
 * - Wi-Fi -> 720p
 * - mobile data, Data Saver on -> 240p, and every rung above 300 MB/hr is locked
 * - mobile data, Data Saver off -> 480p
 * - changing the network or the Saver setting re-applies these defaults
 * - a manual pick holds for the session until one of those changes
 *
 * Clearing a stale manual pick is the caller's job (SettingsRepository.setManualQuality
 * (null) when network or Saver changes); [resolve] additionally refuses to honour a
 * manual pick that has since become locked, so a rung chosen on Wi-Fi cannot leak onto
 * metered data.
 */
class ResolveQualityUseCase(private val catalog: CatalogRepository) {

    /** The rung the rules ask for, ignoring any manual pick. */
    fun defaultFor(network: NetworkState, saver: Boolean): Rung {
        val id = when {
            network == NetworkState.Wifi -> Rung.DEFAULT_WIFI
            // Nothing streams offline; downloads play at whatever rung they were fetched
            // at. The saver default is the safe thing to show until a network returns.
            saver -> Rung.DEFAULT_CELLULAR_SAVER
            else -> Rung.DEFAULT_CELLULAR
        }
        return catalog.rung(id)
    }

    /** What to actually play at, honouring a still-permitted manual pick. */
    fun resolve(network: NetworkState, saver: Boolean, manualRungId: String?): Rung {
        val manual = manualRungId?.let { catalog.rung(it) }
        if (manual != null && !isLocked(manual, network, saver)) return manual
        return defaultFor(network, saver)
    }

    /** Data Saver caps mobile-data streams at 360p (300 MB/hr). */
    fun isLocked(rung: Rung, network: NetworkState, saver: Boolean): Boolean =
        network.isMetered && saver && rung.isAboveSaverCap

    /** The whole sheet, in ladder order. */
    fun options(network: NetworkState, saver: Boolean, currentRungId: String): List<QualityOption> =
        catalog.rungs().map { rung ->
            QualityOption(
                rung = rung,
                selected = rung.id == currentRungId,
                locked = isLocked(rung, network, saver),
            )
        }
}
