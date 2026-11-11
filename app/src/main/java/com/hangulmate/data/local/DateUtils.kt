package com.hangulmate.data.local

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local calendar-day helpers.
 *
 * An "epoch day" is a whole calendar day in the DEVICE's time zone
 * ([ZoneId.systemDefault]) — days since 1970-01-01 in that zone. Day
 * boundaries therefore follow the user's own midnight, not UTC midnight
 * (for a Nepali user the day used to flip at 05:45 NPT under UTC).
 *
 * Every caller (streaks, daily stats, SRS due dates, quiz-day grouping)
 * goes through here, so they all agree on the same day.
 */
object DateUtils {

    /** Epoch day for [epochMillis] (defaults to now), in the device time zone. */
    fun epochDay(epochMillis: Long = System.currentTimeMillis()): Long =
        LocalDate.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault(),
        ).toEpochDay()

    /** Epoch millis at the start (00:00, device time zone) of [epochDay]. */
    fun startOfDayMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** Today as an epoch day, in the device time zone. */
    fun today(): Long = epochDay()
}
