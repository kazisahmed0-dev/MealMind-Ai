package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.calculation.FoodMatcher
import com.example.calculation.PortionEngine
import com.example.calculation.PortionUnit
import com.example.data.database.AppDatabase
import com.example.data.model.ExerciseReminder
import com.example.data.model.FoodItem
import com.example.data.model.MealRecord
import com.example.data.model.ReminderHistoryItem
import com.example.data.model.UserProfile
import com.example.network.gemini.GeminiClient
import com.example.network.gemini.VisionFoodAnalysisResult
import com.example.reminder.ExerciseReminderReceiver
import com.example.reminder.ReminderScheduler
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MealMindRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val foodDao = database.foodDao()
    private val mealDao = database.mealRecordDao()
    private val profileDao = database.userProfileDao()
    private val reminderDao = database.reminderDao()
    private val historyDao = database.reminderHistoryDao()

    fun getAllFoods(): Flow<List<FoodItem>> = foodDao.getAllFoods()

    fun searchFoods(query: String): Flow<List<FoodItem>> = foodDao.searchFoods(query)

    fun getFoodsByCategory(category: String): Flow<List<FoodItem>> = foodDao.getFoodsByCategory(category)

    fun getFavoriteFoods(): Flow<List<FoodItem>> = foodDao.getFavoriteFoods()

    suspend fun getFoodById(id: Long): FoodItem? = foodDao.getFoodById(id)

    suspend fun countFoods(): Int = foodDao.countFoods()

    suspend fun insertCustomFood(food: FoodItem): Long = foodDao.insertFood(food)

    suspend fun updateFood(food: FoodItem) = foodDao.updateFood(food)

    suspend fun deleteFood(food: FoodItem) = foodDao.deleteFood(food)

    suspend fun toggleFavorite(foodId: Long, isFav: Boolean) = foodDao.setFavorite(foodId, isFav)

    // Meals
    fun getRecordsForDate(date: String): Flow<List<MealRecord>> = mealDao.getRecordsForDate(date)

    fun getAllRecords(): Flow<List<MealRecord>> = mealDao.getAllRecords()

    fun getRecentDistinctFoodNames(): Flow<List<String>> = mealDao.getRecentDistinctFoodNames()

    suspend fun logMeal(
        food: FoodItem,
        mealType: String,
        portionUnit: PortionUnit,
        portionQuantity: Double,
        photoUri: String? = null,
        detectedByAi: Boolean = false,
        aiConfidence: Int = 100,
        customDate: String? = null
    ): Long {
        val grams = PortionEngine.calculateGrams(food, portionUnit, portionQuantity)
        val calculated = PortionEngine.calculateNutrition(food, grams)

        val dateStr = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val record = MealRecord(
            mealType = mealType,
            date = dateStr,
            foodId = food.foodId,
            foodName = food.foodName,
            category = food.category,
            portionGrams = calculated.portionGrams,
            portionUnit = portionUnit.name,
            portionUnitQuantity = portionQuantity,
            calories = calculated.calories,
            proteinG = calculated.proteinG,
            carbsG = calculated.carbsG,
            fatG = calculated.fatG,
            fiberG = calculated.fiberG,
            sugarG = calculated.sugarG,
            sodiumMg = calculated.sodiumMg,
            photoUri = photoUri,
            detectedByAi = detectedByAi,
            aiConfidence = aiConfidence
        )

        return mealDao.insertRecord(record)
    }

    suspend fun deleteMealRecord(record: MealRecord) = mealDao.deleteRecord(record)

    suspend fun deleteMealRecordById(id: Long) = mealDao.deleteById(id)

    // Profile
    fun getUserProfile(): Flow<UserProfile?> = profileDao.getProfile()

    suspend fun saveUserProfile(profile: UserProfile) = profileDao.insertOrUpdateProfile(profile)

    // Reminders
    fun getAllReminders(): Flow<List<ExerciseReminder>> = reminderDao.getAllReminders()

    fun getEnabledRemindersFlow(): Flow<List<ExerciseReminder>> = reminderDao.getEnabledRemindersFlow()

    suspend fun getReminderById(id: Long): ExerciseReminder? = reminderDao.getReminderById(id)

    suspend fun saveReminder(reminder: ExerciseReminder): Long {
        val now = System.currentTimeMillis()
        val id = if (reminder.id == 0L) {
            reminderDao.insertReminder(reminder.copy(createdAt = now, updatedAt = now))
        } else {
            // Cancel previous alarm to prevent any ghost schedules
            ReminderScheduler.cancelReminder(context, reminder.id)
            reminderDao.updateReminder(reminder.copy(updatedAt = now))
            reminder.id
        }
        val saved = reminder.copy(id = id, updatedAt = now)
        ReminderScheduler.scheduleReminder(context, saved)
        return id
    }

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) {
        val now = System.currentTimeMillis()
        reminderDao.setReminderEnabled(id, enabled, now)
        val reminder = reminderDao.getReminderById(id)
        if (reminder != null) {
            val updated = reminder.copy(isEnabled = enabled, updatedAt = now)
            if (enabled) {
                ReminderScheduler.scheduleReminder(context, updated)
            } else {
                ReminderScheduler.cancelReminder(context, id)
            }
        }
    }

    suspend fun deleteReminder(reminder: ExerciseReminder) {
        ReminderScheduler.cancelReminder(context, reminder.id)
        reminderDao.deleteReminder(reminder)
    }

    suspend fun testTriggerReminder(reminder: ExerciseReminder) {
        val intent = Intent(context, ExerciseReminderReceiver::class.java).apply {
            action = "com.example.mealmind.TRIGGER_REMINDER"
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE, reminder.exerciseType)
            putExtra(ReminderScheduler.EXTRA_NOTIFICATION_SOUND, reminder.notificationSound)
            putExtra(ReminderScheduler.EXTRA_REPEAT_TYPE, reminder.repeatType)
            putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
        }
        context.sendBroadcast(intent)
    }

    // Reminder History
    fun getAllReminderHistory(): Flow<List<ReminderHistoryItem>> = historyDao.getAllHistory()

    fun getTodayReminderHistory(dateStr: String): Flow<List<ReminderHistoryItem>> = historyDao.getHistoryForDate(dateStr)

    suspend fun recordReminderAction(
        reminderId: Long,
        title: String,
        exerciseType: String,
        status: String,
        snoozeMinutes: Int? = null,
        notes: String = ""
    ) {
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
                status = status,
                snoozeMinutes = snoozeMinutes,
                notes = notes
            )
        )

        val reminder = reminderDao.getReminderById(reminderId)
        if (reminder != null) {
            when (status) {
                "COMPLETED" -> {
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
                        ReminderScheduler.scheduleReminder(context, reminder)
                    }
                }
                "SNOOZED" -> {
                    snoozeMinutes?.let { minutes ->
                        ReminderScheduler.scheduleSnooze(context, reminderId, title, exerciseType, minutes)
                    }
                    reminderDao.updateReminder(
                        reminder.copy(status = "SNOOZED", updatedAt = now)
                    )
                }
                "DISMISSED" -> {
                    if (reminder.repeatType == "ONCE") {
                        reminderDao.updateReminder(
                            reminder.copy(isEnabled = false, status = "EXPIRED", updatedAt = now)
                        )
                    }
                }
            }
        }
    }

    suspend fun clearReminderHistory() = historyDao.clearAllHistory()

    // Matching Engine
    suspend fun matchFoodInDatabase(detectedName: String): Pair<FoodItem?, List<FoodItem>> {
        return FoodMatcher.matchFood(detectedName, foodDao)
    }

    // AI Vision
    suspend fun analyzeFoodImage(uri: Uri): VisionFoodAnalysisResult = withContext(Dispatchers.IO) {
        val base64 = convertImageUriToBase64(uri)
            ?: return@withContext VisionFoodAnalysisResult(
                isFood = false,
                imageQualityOk = false,
                rejectionMessage = "Could not load image file from device storage."
            )
        GeminiClient.analyzeFoodPhoto(base64)
    }

    suspend fun askNutritionAssistant(query: String, history: List<Pair<String, String>> = emptyList()): String {
        return GeminiClient.askNutritionAssistant(query, history)
    }

    private fun convertImageUriToBase64(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale down to max 1024x1024 for fast network transfer
            val maxDimension = 1024
            val scale = (maxDimension.toFloat() / Math.max(bitmap.width, bitmap.height)).coerceAtMost(1.0f)
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
