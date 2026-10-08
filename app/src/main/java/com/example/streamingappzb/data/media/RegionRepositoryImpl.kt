package com.example.streamingappzb.data.media

import android.content.Context
import android.telephony.TelephonyManager
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.domain.repository.RegionRepository
import java.util.Locale

/**
 * Which country's watch-provider data to ask for.
 *
 * Read from the SIM's network country first, then the SIM itself, then the locale. The
 * network beats the locale on purpose: a phone set to `en-US` sitting in Pakistan should
 * see what Pakistani services carry, and locale is a language preference, not a location.
 *
 * The viewer can override it, and that choice wins over everything.
 */
class RegionRepositoryImpl(
    private val context: Context,
    private val prefs: AppPrefs,
) : RegionRepository {

    override fun region(): String = prefs.region() ?: deviceRegion()

    override fun setRegion(code: String) {
        prefs.setRegion(code.trim().uppercase(Locale.ROOT).takeIf { it.length == 2 })
    }

    override fun deviceRegion(): String {
        val telephony = runCatching {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }.getOrNull()

        val candidates = listOf(
            telephony?.networkCountryIso,
            telephony?.simCountryIso,
            Locale.getDefault().country,
        )

        return candidates
            .firstNotNullOfOrNull { iso ->
                iso?.trim()?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
            }
            ?: DEFAULT
    }

    private companion object {
        /**
         * TMDB has provider data for most countries but not all; US is the one region
         * that is always populated, so it is the floor rather than a guess at the user.
         */
        const val DEFAULT = "US"
    }
}
