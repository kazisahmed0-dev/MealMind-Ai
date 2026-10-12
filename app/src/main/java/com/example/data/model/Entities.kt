package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Authoritative food database item with standardized nutrition reference per 100g.
 */
@Entity(tableName = "foods")
data class FoodItem(
    @PrimaryKey(autoGenerate = true)
    val foodId: Long = 0,
    val foodName: String,
    val alternativeNames: String = "", // e.g. "Bhaat, Steamed Rice, Plain Rice"
    val category: String, // "Rice & Grains", "Meat & Poultry", "Fish & Seafood", "Dal & Lentils", "Vegetables", "Fruits", "Eggs & Dairy", "Snacks & Fast Food", "Bakery & Sweets", "Beverages & Drinks"
    val cuisine: String = "Common", // "Bangladeshi", "South Asian / Indian", "Asian", "International"
    val servingReference: String = "100g",
    val caloriesPer100g: Double,
    val proteinGPer100g: Double,
    val carbsGPer100g: Double,
    val fatGPer100g: Double,
    val fiberGPer100g: Double = 0.0,
    val sugarGPer100g: Double = 0.0,
    val sodiumMgPer100g: Double = 0.0,
    val defaultPortionGrams: Double = 100.0,
    val servingUnitName: String = "100g", // e.g. "1 bowl (180g)", "1 piece (50g)"
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false
)

/**
 * User logged meal record calculated deterministically from database values.
 */
@Entity(tableName = "meal_records")
data class MealRecord(
    @PrimaryKey(autoGenerate = true)
    val mealId: Long = 0,
    val mealType: String, // "Breakfast", "Lunch", "Dinner", "Snack"
    val date: String, // YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis(),
    val foodId: Long,
    val foodName: String,
    val category: String,
    val portionGrams: Double,
    val portionUnit: String = "g",
    val portionUnitQuantity: Double = 1.0,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val photoUri: String? = null,
    val detectedByAi: Boolean = false,
    val aiConfidence: Int = 100
)

/**
 * User profile for personal nutrition goals, BMI, and BMR calculations.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "Health Enthusiast",
    val age: Int = 28,
    val gender: String = "Male", // "Male", "Female"
    val heightCm: Double = 172.0,
    val weightKg: Double = 68.0,
    val activityLevel: String = "Moderately Active", // "Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active"
    val calorieGoal: Int = 2100,
    val dietPreference: String = "Balanced"
)

/**
 * Exercise reminder entity for system scheduling.
 */
@Entity(tableName = "exercise_reminders")
data class ExerciseReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String, // e.g. "Morning Exercise"
    val exerciseType: String = "Morning Workout", // "Morning Workout", "Jogging / Running", "Brisk Walk", "Yoga & Stretching", "Strength Training", "Cardio / HIIT", "Cycling", "Swimming", "Evening Walk", "Custom"
    val hour: Int, // 0-23
    val minute: Int, // 0-59
    val repeatType: String = "EVERY_DAY", // "EVERY_DAY", "SPECIFIC_DAYS", "ONCE"
    val daysOfWeek: String = "1,2,3,4,5,6,7", // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val specificDate: String? = null, // "YYYY-MM-DD" for one-time reminders
    val isEnabled: Boolean = true,
    val notificationSound: String = "Default Chime", // "Default Chime", "Gentle Bell", "Energetic Tone", "Silent"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val status: String = "ACTIVE" // "ACTIVE", "COMPLETED", "SNOOZED", "DISABLED"
)

/**
 * Historical record of triggered, completed, snoozed, or dismissed exercise reminders.
 */
@Entity(tableName = "reminder_history")
data class ReminderHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val title: String,
    val exerciseType: String,
    val scheduledTime: String, // e.g. "07:00 AM"
    val scheduledDate: String, // "YYYY-MM-DD"
    val actionTimestamp: Long = System.currentTimeMillis(),
    val status: String, // "COMPLETED", "DISMISSED", "SNOOZED", "MISSED", "TRIGGERED"
    val snoozeMinutes: Int? = null,
    val notes: String = ""
)
