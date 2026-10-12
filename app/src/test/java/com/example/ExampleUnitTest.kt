package com.example

import com.example.data.model.ExerciseReminder
import com.example.reminder.ReminderTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun formatTime12Hour_formatsCorrectly() {
        assertEquals("7:00 AM", ReminderTimeUtils.formatTime12Hour(7, 0))
        assertEquals("12:00 PM", ReminderTimeUtils.formatTime12Hour(12, 0))
        assertEquals("12:30 AM", ReminderTimeUtils.formatTime12Hour(0, 30))
        assertEquals("6:45 PM", ReminderTimeUtils.formatTime12Hour(18, 45))
        assertEquals("11:59 PM", ReminderTimeUtils.formatTime12Hour(23, 59))
    }

    @Test
    fun formatDaysOfWeek_handlesVariousRepeatTypes() {
        assertEquals("Every Day", ReminderTimeUtils.formatDaysOfWeek("EVERY_DAY", "1,2,3,4,5,6,7", null))
        assertEquals("Mon • Wed • Fri", ReminderTimeUtils.formatDaysOfWeek("SPECIFIC_DAYS", "1,3,5", null))
        assertEquals("One-time: Sep 15, 2026", ReminderTimeUtils.formatDaysOfWeek("ONCE", "", "2026-09-15"))
    }

    @Test
    fun getExerciseEmoji_returnsRelevantIcons() {
        assertEquals("🏃", ReminderTimeUtils.getExerciseEmoji("Morning Workout"))
        assertEquals("🚶", ReminderTimeUtils.getExerciseEmoji("Brisk Walk"))
        assertEquals("🧘", ReminderTimeUtils.getExerciseEmoji("Yoga & Stretching"))
        assertEquals("🏋️", ReminderTimeUtils.getExerciseEmoji("Strength Training"))
        assertEquals("🚴", ReminderTimeUtils.getExerciseEmoji("Cycling"))
    }

    @Test
    fun calculateNextTriggerMillis_isAlwaysInFutureForActiveReminder() {
        val now = System.currentTimeMillis()
        val reminder = ExerciseReminder(
            id = 1L,
            title = "Morning Exercise",
            exerciseType = "Morning Workout",
            hour = 7,
            minute = 0,
            repeatType = "EVERY_DAY",
            daysOfWeek = "1,2,3,4,5,6,7",
            isEnabled = true
        )

        val nextTrigger = ReminderTimeUtils.calculateNextTriggerMillis(reminder)
        assertNotNull(nextTrigger)
        assertTrue(nextTrigger!! > now)
    }

    @Test
    fun calculateNextTriggerMillis_returnsNullForDisabledReminder() {
        val reminder = ExerciseReminder(
            id = 2L,
            title = "Disabled",
            exerciseType = "Walk",
            hour = 8,
            minute = 0,
            isEnabled = false
        )

        val nextTrigger = ReminderTimeUtils.calculateNextTriggerMillis(reminder)
        assertEquals(null, nextTrigger)
    }
}

