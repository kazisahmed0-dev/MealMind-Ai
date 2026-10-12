package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ExerciseReminder
import com.example.data.model.FoodItem
import com.example.data.model.MealRecord
import com.example.data.model.ReminderHistoryItem
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY foodName ASC")
    fun getAllFoods(): Flow<List<FoodItem>>

    @Query("SELECT * FROM foods WHERE foodName LIKE '%' || :query || '%' OR alternativeNames LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY foodName ASC")
    fun searchFoods(query: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM foods WHERE category = :category ORDER BY foodName ASC")
    fun getFoodsByCategory(category: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM foods WHERE isFavorite = 1 ORDER BY foodName ASC")
    fun getFavoriteFoods(): Flow<List<FoodItem>>

    @Query("SELECT * FROM foods WHERE foodId = :id LIMIT 1")
    suspend fun getFoodById(id: Long): FoodItem?

    @Query("SELECT * FROM foods WHERE LOWER(foodName) = LOWER(:name) LIMIT 1")
    suspend fun getFoodByName(name: String): FoodItem?

    @Query("SELECT * FROM foods WHERE LOWER(foodName) LIKE '%' || LOWER(:name) || '%' OR LOWER(alternativeNames) LIKE '%' || LOWER(:name) || '%' LIMIT 10")
    suspend fun findCandidates(name: String): List<FoodItem>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countFoods(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItem>)

    @Update
    suspend fun updateFood(food: FoodItem)

    @Delete
    suspend fun deleteFood(food: FoodItem)

    @Query("UPDATE foods SET isFavorite = :isFav WHERE foodId = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)
}

@Dao
interface MealRecordDao {
    @Query("SELECT * FROM meal_records WHERE date = :date ORDER BY timestamp DESC")
    fun getRecordsForDate(date: String): Flow<List<MealRecord>>

    @Query("SELECT * FROM meal_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<MealRecord>>

    @Query("SELECT DISTINCT foodName FROM meal_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDistinctFoodNames(limit: Int = 10): Flow<List<String>>

    @Query("SELECT * FROM meal_records WHERE mealId = :id LIMIT 1")
    suspend fun getRecordById(id: Long): MealRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MealRecord): Long

    @Delete
    suspend fun deleteRecord(record: MealRecord)

    @Query("DELETE FROM meal_records WHERE mealId = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM exercise_reminders ORDER BY hour ASC, minute ASC")
    fun getAllReminders(): Flow<List<ExerciseReminder>>

    @Query("SELECT * FROM exercise_reminders WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    fun getEnabledRemindersFlow(): Flow<List<ExerciseReminder>>

    @Query("SELECT * FROM exercise_reminders WHERE isEnabled = 1")
    suspend fun getEnabledReminders(): List<ExerciseReminder>

    @Query("SELECT * FROM exercise_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ExerciseReminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ExerciseReminder): Long

    @Update
    suspend fun updateReminder(reminder: ExerciseReminder)

    @Delete
    suspend fun deleteReminder(reminder: ExerciseReminder)

    @Query("DELETE FROM exercise_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("UPDATE exercise_reminders SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReminderEnabled(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE exercise_reminders SET status = :status, lastTriggeredAt = :lastTriggered, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateReminderStatus(id: Long, status: String, lastTriggered: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ReminderHistoryDao {
    @Query("SELECT * FROM reminder_history ORDER BY actionTimestamp DESC")
    fun getAllHistory(): Flow<List<ReminderHistoryItem>>

    @Query("SELECT * FROM reminder_history WHERE scheduledDate = :date ORDER BY actionTimestamp DESC")
    fun getHistoryForDate(date: String): Flow<List<ReminderHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ReminderHistoryItem): Long

    @Query("DELETE FROM reminder_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM reminder_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)
}
