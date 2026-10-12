package com.example.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.ReminderHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_DONE = "com.example.mealmind.ACTION_DONE"
        const val ACTION_SNOOZE = "com.example.mealmind.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.example.mealmind.ACTION_DISMISS"

        const val EXTRA_SNOOZE_MINUTES = "EXTRA_SNOOZE_MINUTES"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Exercise Reminder"
        val exerciseType = intent.getStringExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE) ?: "Morning Workout"
        val notificationId = reminderId.toInt()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)

        val action = intent.action ?: return
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(now))

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val reminderDao = db.reminderDao()
                val historyDao = db.reminderHistoryDao()

                val reminder = if (reminderId > 0) reminderDao.getReminderById(reminderId) else null

                when (action) {
                    ACTION_DONE -> {
                        Log.d(TAG, "Reminder $reminderId marked DONE")
                        historyDao.insertHistory(
                            ReminderHistoryItem(
                                reminderId = reminderId,
                                title = title,
                                exerciseType = exerciseType,
                                scheduledTime = timeStr,
                                scheduledDate = dateStr,
                                actionTimestamp = now,
                                status = "COMPLETED"
                            )
                        )

                        if (reminder != null) {
                            if (reminder.repeatType == "ONCE") {
                                reminderDao.updateReminder(
                                    reminder.copy(
                                        isEnabled = false,
                                        status = "COMPLETED",
                                        lastTriggeredAt = now,
                                        updatedAt = now
                                    )
                                )
                            } else {
                                reminderDao.updateReminder(
                                    reminder.copy(
                                        status = "ACTIVE",
                                        lastTriggeredAt = now,
                                        updatedAt = now
                                    )
                                )
                                // Schedule next recurrence
                                ReminderScheduler.scheduleReminder(context, reminder)
                            }
                        }
                    }

                    ACTION_SNOOZE -> {
                        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                        Log.d(TAG, "Reminder $reminderId snoozed for $snoozeMinutes minutes")

                        ReminderScheduler.scheduleSnooze(
                            context = context,
                            reminderId = reminderId,
                            title = title,
                            exerciseType = exerciseType,
                            snoozeMinutes = snoozeMinutes
                        )

                        historyDao.insertHistory(
                            ReminderHistoryItem(
                                reminderId = reminderId,
                                title = title,
                                exerciseType = exerciseType,
                                scheduledTime = timeStr,
                                scheduledDate = dateStr,
                                actionTimestamp = now,
                                status = "SNOOZED",
                                snoozeMinutes = snoozeMinutes,
                                notes = "Snoozed for $snoozeMinutes min"
                            )
                        )

                        if (reminder != null) {
                            reminderDao.updateReminder(
                                reminder.copy(
                                    status = "SNOOZED",
                                    updatedAt = now
                                )
                            )
                        }
                    }

                    ACTION_DISMISS -> {
                        Log.d(TAG, "Reminder $reminderId DISMISSED")
                        historyDao.insertHistory(
                            ReminderHistoryItem(
                                reminderId = reminderId,
                                title = title,
                                exerciseType = exerciseType,
                                scheduledTime = timeStr,
                                scheduledDate = dateStr,
                                actionTimestamp = now,
                                status = "DISMISSED"
                            )
                        )

                        if (reminder != null && reminder.repeatType == "ONCE") {
                            reminderDao.updateReminder(
                                reminder.copy(
                                    isEnabled = false,
                                    status = "EXPIRED",
                                    updatedAt = now
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
