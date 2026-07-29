package com.zame.nutrinest.util

import java.util.Calendar

object WeekUtils {

    val DAY_NAMES = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

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
        weekStartOf(time) + 7L * 24 * 60 * 60 * 1000

    fun previousWeekStart(weekStart: Long): Long =
        weekStart - 7L * 24 * 60 * 60 * 1000

    /** 0 = Monday … 6 = Sunday for "today". */
    fun todayIndex(): Int {
        val cal = Calendar.getInstance()
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
