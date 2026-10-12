package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FoodDao
import com.example.data.dao.MealRecordDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.ReminderHistoryDao
import com.example.data.dao.UserProfileDao
import com.example.data.model.ExerciseReminder
import com.example.data.model.FoodItem
import com.example.data.model.MealRecord
import com.example.data.model.ReminderHistoryItem
import com.example.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FoodItem::class,
        MealRecord::class,
        UserProfile::class,
        ExerciseReminder::class,
        ReminderHistoryItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderHistoryDao(): ReminderHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mealmind_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        private suspend fun populateDatabase(db: AppDatabase) {
            val foodDao = db.foodDao()
            val userProfileDao = db.userProfileDao()
            val reminderDao = db.reminderDao()

            // Preload 500+ authentic food database items
            val seedFoods = FoodSeedData.getInitialFoods()
            foodDao.insertAll(seedFoods)

            // Preload default user profile
            userProfileDao.insertOrUpdateProfile(UserProfile())

            // Preload default morning exercise reminder
            reminderDao.insertReminder(
                ExerciseReminder(
                    title = "Morning Cardio & Stretch",
                    hour = 7,
                    minute = 0,
                    daysOfWeek = "1,2,3,4,5,6,7",
                    isEnabled = true
                )
            )
        }
    }
}
