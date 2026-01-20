package com.example.quandointv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object NotificationScheduler {
    fun scheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("notifiche", Context.MODE_PRIVATE)

        val hour = prefs.getInt("hour", 0)
        val minute = prefs.getInt("minute", 0)

        val days = listOf(
            "lun" to Calendar.MONDAY,
            "mar" to Calendar.TUESDAY,
            "mer" to Calendar.WEDNESDAY,
            "gio" to Calendar.THURSDAY,
            "ven" to Calendar.FRIDAY,
            "sab" to Calendar.SATURDAY,
            "dom" to Calendar.SUNDAY
        )

        for ((key, dayOfWeek) in days) {
            if (!prefs.getBoolean(key, false)) continue

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("day", dayOfWeek)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dayOfWeek,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (before(Calendar.getInstance())) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            }

        }
    }
}