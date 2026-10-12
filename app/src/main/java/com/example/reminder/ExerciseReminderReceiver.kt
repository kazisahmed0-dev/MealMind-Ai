package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.model.ReminderHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExerciseReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExerciseReminderReceiver"
        const val CHANNEL_ID = "mealmind_exercise_alerts_v2"
        const val EXTRA_SHOW_ALARM_DIALOG = "EXTRA_SHOW_ALARM_DIALOG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Exercise Reminder"
        val exerciseType = intent.getStringExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE) ?: "Morning Workout"
        val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
        val soundOption = intent.getStringExtra(ReminderScheduler.EXTRA_NOTIFICATION_SOUND) ?: "Default Chime"
        val repeatType = intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT_TYPE) ?: "EVERY_DAY"

        Log.d(TAG, "Triggered reminder: id=$reminderId, title=$title, exerciseType=$exerciseType, isSnooze=$isSnooze")

        showNotification(
            context = context,
            reminderId = reminderId,
            title = title,
            exerciseType = exerciseType,
            isSnooze = isSnooze,
            soundOption = soundOption
        )

        // Record triggered in history and handle one-time / recurring transition
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val historyDao = db.reminderHistoryDao()
                val reminderDao = db.reminderDao()
                val now = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
                val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(now))

                historyDao.insertHistory(
                    ReminderHistoryItem(
                        reminderId = reminderId,
                        title = title,
                        exerciseType = exerciseType,
                        scheduledTime = timeStr,
                        scheduledDate = dateStr,
                        actionTimestamp = now,
                        status = if (isSnooze) "SNOOZE_ALERT" else "TRIGGERED"
                    )
                )

                val reminder = reminderDao.getReminderById(reminderId)
                if (reminder != null) {
                    if (reminder.repeatType == "ONCE") {
                        // Mark completed/expired so it doesn't repeat
                        reminderDao.updateReminder(
                            reminder.copy(
                                isEnabled = false,
                                status = "COMPLETED",
                                lastTriggeredAt = now,
                                updatedAt = now
                            )
                        )
                    } else {
                        // Recurring: update last triggered and re-schedule next occurrence
                        reminderDao.updateReminder(
                            reminder.copy(
                                status = "ACTIVE",
                                lastTriggeredAt = now,
                                updatedAt = now
                            )
                        )
                        ReminderScheduler.scheduleReminder(context, reminder)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ExerciseReminderReceiver async work", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        reminderId: Long,
        title: String,
        exerciseType: String,
        isSnooze: Boolean,
        soundOption: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = reminderId.toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Exercise & Workout Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms for scheduled workouts, daily walks, and movement"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                if (soundOption != "Silent") {
                    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        defaultSoundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    setSound(null, null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification -> open app and show active alarm modal
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_ALARM_DIALOG, true)
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE, exerciseType)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (reminderId * 10).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Done
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE, exerciseType)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 1).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10 min
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE, exerciseType)
            putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss
        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DISMISS
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE, exerciseType)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 3).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = ReminderTimeUtils.getExerciseEmoji(exerciseType)
        val alertHeader = if (isSnooze) "⏰ Snoozed Exercise Alarm" else "$emoji Exercise Reminder"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alertHeader)
            .setContentText("It's time for your scheduled $exerciseType: $title")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("It's time for your scheduled $exerciseType: $title. Keep your energy high and stay consistent!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze 10m", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
