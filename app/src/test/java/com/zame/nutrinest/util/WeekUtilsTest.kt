package com.zame.nutrinest.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeekUtilsTest {

    private lateinit var originalZone: TimeZone

    @Before
    fun useMadridTime() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `next week remains Monday midnight across spring DST`() {
        val monday = localTime(2026, Calendar.MARCH, 23)
        val next = WeekUtils.nextWeekStart(monday)
        val calendar = Calendar.getInstance().apply { timeInMillis = next }

        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `previous week remains Monday midnight across autumn DST`() {
        val monday = localTime(2026, Calendar.OCTOBER, 26)
        val previous = WeekUtils.previousWeekStart(monday)
        val calendar = Calendar.getInstance().apply { timeInMillis = previous }

        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(19, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun localTime(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis
}
