package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.ExerciseReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
    const val EXTRA_REMINDER_TITLE = "EXTRA_REMINDER_TITLE"
    const val EXTRA_EXERCISE_TYPE = "EXTRA_EXERCISE_TYPE"
    const val EXTRA_NOTIFICATION_SOUND = "EXTRA_NOTIFICATION_SOUND"
    const val EXTRA_REPEAT_TYPE = "EXTRA_REPEAT_TYPE"
    const val EXTRA_IS_SNOOZE = "EXTRA_IS_SNOOZE"

    fun scheduleReminder(context: Context, reminder: ExerciseReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Step 1: Always cancel previous scheduled intent for this ID to guarantee NO DUPLICATES
        cancelReminder(context, reminder.id)

        if (!reminder.isEnabled) {
            Log.d(TAG, "Reminder ${reminder.id} is disabled. Skipping schedule.")
            return
        }

        val nextTriggerMillis = ReminderTimeUtils.calculateNextTriggerMillis(reminder)
        if (nextTriggerMillis == null) {
            Log.d(TAG, "Reminder ${reminder.id} has no valid future trigger time.")
            return
        }

        val intent = Intent(context, ExerciseReminderReceiver::class.java).apply {
            action = "com.example.mealmind.TRIGGER_REMINDER"
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_EXERCISE_TYPE, reminder.exerciseType)
            putExtra(EXTRA_NOTIFICATION_SOUND, reminder.notificationSound)
            putExtra(EXTRA_REPEAT_TYPE, reminder.repeatType)
            putExtra(EXTRA_IS_SNOOZE, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled reminder ${reminder.id} for $nextTriggerMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException scheduling exact alarm. Falling back to set()", e)
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule reminder", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error scheduling reminder", e)
        }
    }

    fun scheduleSnooze(
        context: Context,
        reminderId: Long,
        title: String,
        exerciseType: String,
        snoozeMinutes: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val snoozeRequestCode = (reminderId * 1000 + 99).toInt()

        val intent = Intent(context, ExerciseReminderReceiver::class.java).apply {
            action = "com.example.mealmind.TRIGGER_REMINDER"
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_EXERCISE_TYPE, exerciseType)
            putExtra(EXTRA_NOTIFICATION_SOUND, "Default Chime")
            putExtra(EXTRA_REPEAT_TYPE, "EVERY_DAY")
            putExtra(EXTRA_IS_SNOOZE, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled snooze for reminder $reminderId in $snoozeMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling snooze", e)
        }
    }

    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Cancel standard alarm
        val mainIntent = Intent(context, ExerciseReminderReceiver::class.java).apply {
            action = "com.example.mealmind.TRIGGER_REMINDER"
        }
        val mainPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(mainPendingIntent)

        // Cancel snooze alarm
        val snoozeRequestCode = (reminderId * 1000 + 99).toInt()
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePendingIntent)
    }

    fun rescheduleAllEnabledReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val enabledReminders = db.reminderDao().getEnabledReminders()
                Log.d(TAG, "Rescheduling ${enabledReminders.size} enabled reminders")
                for (reminder in enabledReminders) {
                    scheduleReminder(context, reminder)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling reminders on boot/timezone change", e)
            }
        }
    }
}
