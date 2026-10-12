package com.example.reminder

import com.example.data.model.ExerciseReminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReminderTimeUtils {

    fun formatTime12Hour(hour: Int, minute: Int): String {
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val m = String.format(Locale.US, "%02d", minute)
        val amPm = if (hour < 12) "AM" else "PM"
        return "$h:$m $amPm"
    }

    fun parseTime(timeStr: String): Pair<Int, Int>? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                val h = parts[0].trim().toInt()
                val m = parts[1].trim().take(2).toInt()
                val isPm = timeStr.uppercase(Locale.US).contains("PM")
                val isAm = timeStr.uppercase(Locale.US).contains("AM")
                val finalHour = when {
                    isPm && h < 12 -> h + 12
                    isAm && h == 12 -> 0
                    else -> h
                }
                Pair(finalHour, m)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun formatDaysOfWeek(repeatType: String, daysOfWeek: String, specificDate: String? = null): String {
        return when (repeatType) {
            "ONCE" -> {
                if (!specificDate.isNullOrBlank()) {
                    try {
                        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
                        val d = parser.parse(specificDate)
                        if (d != null) "One-time: ${formatter.format(d)}" else "One-time"
                    } catch (e: Exception) {
                        "One-time: $specificDate"
                    }
                } else {
                    "One-time"
                }
            }
            "EVERY_DAY" -> "Every Day"
            "SPECIFIC_DAYS" -> {
                val dayList = daysOfWeek.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .distinct()
                    .sorted()

                if (dayList.size == 7) {
                    "Every Day"
                } else if (dayList == listOf(1, 2, 3, 4, 5)) {
                    "Mon • Tue • Wed • Thu • Fri"
                } else if (dayList == listOf(6, 7)) {
                    "Sat • Sun"
                } else if (dayList.isEmpty()) {
                    "No days selected"
                } else {
                    val dayNames = mapOf(
                        1 to "Mon",
                        2 to "Tue",
                        3 to "Wed",
                        4 to "Thu",
                        5 to "Fri",
                        6 to "Sat",
                        7 to "Sun"
                    )
                    dayList.mapNotNull { dayNames[it] }.joinToString(" • ")
                }
            }
            else -> "Every Day"
        }
    }

    /**
     * Calculates the exact epoch millis for the next occurrence of this reminder.
     * Respects local timezone.
     */
    fun calculateNextTriggerMillis(
        reminder: ExerciseReminder,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (!reminder.isEnabled) return null

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }

        when (reminder.repeatType) {
            "ONCE" -> {
                val cal = Calendar.getInstance()
                if (!reminder.specificDate.isNullOrBlank()) {
                    try {
                        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val date = parser.parse(reminder.specificDate)
                        if (date != null) {
                            cal.time = date
                        }
                    } catch (e: Exception) {
                        // fallback to today/tomorrow
                    }
                }
                cal.set(Calendar.HOUR_OF_DAY, reminder.hour)
                cal.set(Calendar.MINUTE, reminder.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                // If specificDate wasn't provided, and time is past today, schedule for tomorrow
                if (reminder.specificDate.isNullOrBlank() && cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }

                return if (cal.timeInMillis > nowMillis) {
                    cal.timeInMillis
                } else {
                    null // In the past / expired
                }
            }
            "EVERY_DAY" -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                    set(Calendar.MINUTE, reminder.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
            "SPECIFIC_DAYS" -> {
                val daySet = reminder.daysOfWeek.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet()

                if (daySet.isEmpty()) return null

                for (dayOffset in 0..7) {
                    val candidate = Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        add(Calendar.DAY_OF_YEAR, dayOffset)
                        set(Calendar.HOUR_OF_DAY, reminder.hour)
                        set(Calendar.MINUTE, reminder.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val candidateDayNumber = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 1
                    }

                    if (daySet.contains(candidateDayNumber) && candidate.timeInMillis > nowMillis) {
                        return candidate.timeInMillis
                    }
                }

                // If 7 days check wrapped, look up next week
                val fallback = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                    set(Calendar.MINUTE, reminder.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return fallback.timeInMillis
            }
            else -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                    set(Calendar.MINUTE, reminder.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
        }
    }

    /**
     * Human-readable next trigger string, e.g. "Tomorrow at 7:00 AM" or "Today at 6:00 PM".
     */
    fun formatRelativeNextTrigger(
        reminder: ExerciseReminder,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (!reminder.isEnabled) return "OFF (Disabled)"

        val nextMillis = calculateNextTriggerMillis(reminder, nowMillis)
            ?: return if (reminder.repeatType == "ONCE") "Completed / Expired" else "Disabled"

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextMillis }

        val timeStr = formatTime12Hour(reminder.hour, reminder.minute)

        val sameYear = nowCal.get(Calendar.YEAR) == nextCal.get(Calendar.YEAR)
        val dayDiff = nextCal.get(Calendar.DAY_OF_YEAR) - nowCal.get(Calendar.DAY_OF_YEAR)

        return when {
            sameYear && dayDiff == 0 -> "Today at $timeStr"
            sameYear && dayDiff == 1 -> "Tomorrow at $timeStr"
            sameYear && dayDiff in 2..6 -> {
                val dayFormat = SimpleDateFormat("EEEE", Locale.US)
                "${dayFormat.format(nextCal.time)} at $timeStr"
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d", Locale.US)
                "${dateFormat.format(nextCal.time)} at $timeStr"
            }
        }
    }

    fun getExerciseEmoji(exerciseType: String): String {
        return when (exerciseType.lowercase(Locale.US)) {
            "morning workout" -> "🏃"
            "jogging / running", "jogging", "running" -> "🏃"
            "brisk walk", "evening walk", "walk", "walking" -> "🚶"
            "yoga & stretching", "yoga", "stretching" -> "🧘"
            "strength training", "strength", "weights", "gym" -> "🏋️"
            "cardio / hiit", "cardio", "hiit" -> "🔥"
            "cycling", "bike" -> "🚴"
            "swimming", "swim" -> "🏊"
            else -> "⏰"
        }
    }
}
