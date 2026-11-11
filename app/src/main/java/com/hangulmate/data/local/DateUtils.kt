package com.hangulmate.data.local

import java.util.concurrent.TimeUnit

/** Epoch-day helpers. An "epoch day" is whole days since 1970-01-01 UTC. */
object DateUtils {
    const val MILLIS_PER_DAY: Long = 86_400_000L

    /** Epoch day for [epochMillis] (defaults to now). */
    fun epochDay(epochMillis: Long = System.currentTimeMillis()): Long =
        TimeUnit.MILLISECONDS.toDays(epochMillis)

    /** Epoch millis at the start (00:00 UTC) of [epochDay]. */
    fun startOfDayMillis(epochDay: Long): Long = epochDay * MILLIS_PER_DAY

    /** Today as an epoch day. */
    fun today(): Long = epochDay()
}
