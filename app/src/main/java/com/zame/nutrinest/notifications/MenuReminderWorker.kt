package com.zame.nutrinest.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zame.nutrinest.MainActivity
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.util.WeekUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Friday reminder: checks whether next week's menu exists and, if not,
 * posts a friendly notification. Runs weekly, anchored to Friday morning.
 */
class MenuReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NutriNestApp ?: return Result.success()
        val nextWeek = WeekUtils.nextWeekStart()
        val alreadyPlanned = app.container.menuRepository.hasMenu(nextWeek)
        if (!alreadyPlanned) {
            sendReminder(applicationContext)
        }
        return Result.success()
    }

    private fun sendReminder(context: Context) {
        // Respect the runtime permission on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_detail) }
        )

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "menu_reminders"
        private const val NOTIFICATION_ID = 4001
        private const val WORK_NAME = "weekly_menu_reminder"

        /** Schedules the weekly reminder, anchored near Friday 10:00. */
        fun schedule(context: Context) {
            val delayMs = millisUntilNextFridayMorning()
            val request = PeriodicWorkRequestBuilder<MenuReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // Re-anchor installations that were previously scheduled for
                // Saturday so they receive the next reminder on Friday.
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }

        internal fun millisUntilNextFridayMorning(now: Calendar = Calendar.getInstance()): Long {
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
