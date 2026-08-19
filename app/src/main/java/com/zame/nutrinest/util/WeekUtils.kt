package com.zame.nutrinest.util

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

object WeekUtils {

    /**
     * Day names for the device's language.
     *
     * Taken from the JDK rather than `strings.xml`: the platform already knows
     * every locale's day names and capitalisation conventions, so translating
     * them by hand would only be a chance to get them wrong.
     */
    fun dayNames(locale: Locale = Locale.getDefault()): List<String> =
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) }

    /** Three-letter forms, for chips and other tight spots. */
    fun shortDayNames(locale: Locale = Locale.getDefault()): List<String> =
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }

    /** Epoch millis of Monday 00:00 (local) of the week containing [time]. */
    fun weekStartOf(time: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Calendar.DAY_OF_WEEK: SUNDAY=1..SATURDAY=7 → 0=Monday..6=Sunday
        val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -dayIndex)
        return cal.timeInMillis
    }

    fun nextWeekStart(time: Long = System.currentTimeMillis()): Long =
        shiftedWeekStart(weekStartOf(time), 1)

    fun previousWeekStart(weekStart: Long): Long =
        shiftedWeekStart(weekStartOf(weekStart), -1)

    private fun shiftedWeekStart(weekStart: Long, weeks: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = weekStart
            add(Calendar.WEEK_OF_YEAR, weeks)
        }.timeInMillis

    /** 0 = Monday … 6 = Sunday for "today". */
    fun todayIndex(): Int {
        val cal = Calendar.getInstance()
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    /** Epoch millis of local midnight — the key a day's intake log is filed under. */
    fun dayStartOf(time: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** 0 = Monday … 6 = Sunday for the day starting at [dayStart]. */
    fun dayIndexOf(dayStart: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    /**
     * How far through [dayStart] we are, as an hour 0–24.
     *
     * A day still ahead reads 0 (every meal is to come) and a day already gone
     * reads 24 (none are), so rebalancing a past or future day does the
     * sensible thing without the caller having to check which it is.
     */
    fun hourWithin(dayStart: Long, now: Long = System.currentTimeMillis()): Int {
        val today = dayStartOf(now)
        return when {
            dayStart > today -> 0
            dayStart < today -> 24
            else -> Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        }
    }
}
