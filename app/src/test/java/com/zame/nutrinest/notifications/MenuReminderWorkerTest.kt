package com.zame.nutrinest.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MenuReminderWorkerTest {

    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    @Test
    fun `a Thursday schedules the reminder for Friday at ten`() {
        val now = calendar(2026, Calendar.AUGUST, 27, 13, 30)
        val scheduled = scheduledTime(now)

        assertEquals(Calendar.FRIDAY, scheduled.get(Calendar.DAY_OF_WEEK))
        assertEquals(28, scheduled.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, scheduled.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, scheduled.get(Calendar.MINUTE))
    }

    @Test
    fun `Friday at ten schedules the following Friday rather than now`() {
        val now = calendar(2026, Calendar.AUGUST, 28, 10, 0)
        val scheduled = scheduledTime(now)

        assertEquals(Calendar.FRIDAY, scheduled.get(Calendar.DAY_OF_WEEK))
        assertEquals(4, scheduled.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, scheduled.get(Calendar.MONTH))
        assertEquals(10, scheduled.get(Calendar.HOUR_OF_DAY))
    }

    private fun scheduledTime(now: Calendar): Calendar {
        val delay = MenuReminderWorker.millisUntilNextFridayMorning(now)
        return (now.clone() as Calendar).apply { timeInMillis += delay }
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(madrid).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }
}
