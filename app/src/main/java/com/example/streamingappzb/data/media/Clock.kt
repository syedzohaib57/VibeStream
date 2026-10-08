package com.example.streamingappzb.data.media

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Time, behind an interface.
 *
 * Several rules here are date-dependent — what counts as upcoming, which anime season is
 * current, whether a cache entry is stale — and none of them are testable against a real
 * clock. This is the seam.
 */
interface Clock {
    fun nowMillis(): Long

    /** Today as `yyyy-MM-dd`, in the device's own zone. */
    fun todayIso(): String

    /** 1-12. */
    fun month(): Int

    fun year(): Int
}

class SystemClock(
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : Clock {

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun todayIso(): String = with(calendar()) {
        "%04d-%02d-%02d".format(
            get(Calendar.YEAR),
            get(Calendar.MONTH) + 1,
            get(Calendar.DAY_OF_MONTH),
        )
    }

    override fun month(): Int = calendar().get(Calendar.MONTH) + 1

    override fun year(): Int = calendar().get(Calendar.YEAR)

    private fun calendar(): Calendar = Calendar.getInstance(timeZone, Locale.ROOT).apply {
        timeInMillis = nowMillis()
    }
}
