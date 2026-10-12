package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculation.BmiResult
import com.example.calculation.BmrTdeeResult
import com.example.calculation.CalculatedNutrition
import com.example.calculation.HealthCalculators
import com.example.calculation.PortionEngine
import com.example.calculation.PortionUnit
import com.example.data.model.ExerciseReminder
import com.example.data.model.FoodItem
import com.example.data.model.MealRecord
import com.example.data.model.ReminderHistoryItem
import com.example.data.model.UserProfile
import com.example.data.repository.MealMindRepository
import com.example.reminder.ReminderTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppDestination(val title: String) {
    DASHBOARD("Daily Log"),
    EXERCISE_REMINDERS("Reminders"),
    SCANNER("AI Food Vision"),
    FOOD_DATABASE("Food Catalog"),
    PROFILE_TOOLS("Health Tools"),
    AI_ASSISTANT("Nutrition AI")
}

data class AnalyzedItemUiState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val detectedName: String,
    val matchedFood: FoodItem?,
    val alternativeCandidates: List<FoodItem> = emptyList(),
    val confidence: Int,
    val portionUnit: PortionUnit = PortionUnit.GRAMS,
    val portionQuantity: Double = 100.0,
    val calculatedNutrition: CalculatedNutrition = CalculatedNutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val clarificationOptions: List<String> = emptyList(),
    val userConfirmed: Boolean = true
)

data class ChatMessage(
    val sender: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MealMindViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MealMindRepository(application)

    // Current navigation destination
    private val _currentDestination = MutableStateFlow(AppDestination.DASHBOARD)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    // Selected date for log view (defaults to today)
    val todayDateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val _selectedDate = MutableStateFlow(todayDateString)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // User profile
    val userProfile: StateFlow<UserProfile> = repository.getUserProfile()
        .map { it ?: UserProfile() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserProfile()
        )

    // Today's meal records
    val todayMeals: StateFlow<List<MealRecord>> = _selectedDate
        .combine(repository.getAllRecords()) { date, _ -> date }
        .combine(repository.getRecordsForDate(todayDateString)) { _, records -> records }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All-time meal records
    val allMealRecords: StateFlow<List<MealRecord>> = repository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recent foods for quick logging
    val recentFoodNames: StateFlow<List<String>> = repository.getRecentDistinctFoodNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Food Database state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val allFoods: StateFlow<List<FoodItem>> = repository.getAllFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exercise reminders
    val reminders: StateFlow<List<ExerciseReminder>> = repository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminderHistory: StateFlow<List<ReminderHistoryItem>> = repository.getAllReminderHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextUpcomingReminder: StateFlow<Pair<ExerciseReminder, String>?> = reminders
        .map { list ->
            val enabledList = list.filter { it.isEnabled }
            if (enabledList.isEmpty()) return@map null
            val now = System.currentTimeMillis()
            val candidateWithMillis = enabledList.mapNotNull { rem ->
                val nextMillis = ReminderTimeUtils.calculateNextTriggerMillis(rem, now)
                if (nextMillis != null) Pair(rem, nextMillis) else null
            }.minByOrNull { it.second }

            candidateWithMillis?.let {
                val formatted = ReminderTimeUtils.formatRelativeNextTrigger(it.first, now)
                Pair(it.first, formatted)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // In-app triggered active alarm dialog
    private val _activeAlarmTriggered = MutableStateFlow<ExerciseReminder?>(null)
    val activeAlarmTriggered: StateFlow<ExerciseReminder?> = _activeAlarmTriggered.asStateFlow()

    // AI Vision State
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _detectedFoodItems = MutableStateFlow<List<AnalyzedItemUiState>>(emptyList())
    val detectedFoodItems: StateFlow<List<AnalyzedItemUiState>> = _detectedFoodItems.asStateFlow()

    private val _selectedMealType = MutableStateFlow("Lunch")
    val selectedMealType: StateFlow<String> = _selectedMealType.asStateFlow()

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()

    // AI Assistant Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "model",
                text = "Hello! I am your MealMind AI Nutrition Assistant. Ask me anything about healthy eating, macronutrients, calorie tracking, or meal choices!"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun navigateTo(dest: AppDestination) {
        _currentDestination.value = dest
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun setSelectedMealType(type: String) {
        _selectedMealType.value = type
    }

    fun clearSaveMessage() {
        _saveSuccessMessage.value = null
    }

    // AI Vision Operations
    fun setImageForAnalysis(uri: Uri) {
        _selectedImageUri.value = uri
        _analysisError.value = null
        _detectedFoodItems.value = emptyList()
        analyzeFoodPhoto(uri)
    }

    fun clearScanner() {
        _selectedImageUri.value = null
        _analysisError.value = null
        _detectedFoodItems.value = emptyList()
    }

    private fun analyzeFoodPhoto(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null

            val result = repository.analyzeFoodImage(uri)

            if (!result.isFood) {
                _analysisError.value = result.rejectionMessage ?: "The selected photo does not appear to be food or drink. Please snap a clear photo of your meal."
                _isAnalyzing.value = false
                return@launch
            }

            if (!result.imageQualityOk) {
                _analysisError.value = result.rejectionMessage ?: "The image quality is too low or blurry. Please take a well-lit photo."
                _isAnalyzing.value = false
                return@launch
            }

            if (result.items.isEmpty()) {
                _analysisError.value = "No food items could be detected. Try searching or selecting manually from our 500+ database."
                _isAnalyzing.value = false
                return@launch
            }

            // Match each detected food item with authoritative Database!
            val uiItems = mutableListOf<AnalyzedItemUiState>()
            for (detected in result.items) {
                val (matched, candidates) = repository.matchFoodInDatabase(detected.detectedName)
                val targetFood = matched ?: candidates.firstOrNull()

                val portionGrams = if (detected.estimatedGrams > 0) detected.estimatedGrams else (targetFood?.defaultPortionGrams ?: 150.0)
                val nutrition = if (targetFood != null) {
                    PortionEngine.calculateNutrition(targetFood, portionGrams)
                } else {
                    CalculatedNutrition(portionGrams, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                }

                uiItems.add(
                    AnalyzedItemUiState(
                        detectedName = detected.detectedName,
                        matchedFood = targetFood,
                        alternativeCandidates = candidates,
                        confidence = detected.confidence,
                        portionUnit = PortionUnit.GRAMS,
                        portionQuantity = portionGrams,
                        calculatedNutrition = nutrition,
                        needsClarification = detected.needsClarification || targetFood == null,
                        clarificationQuestion = detected.clarificationQuestion ?: if (targetFood == null) "Which food matches best?" else null,
                        clarificationOptions = detected.clarificationOptions
                    )
                )
            }

            _detectedFoodItems.value = uiItems
            _isAnalyzing.value = false
        }
    }

    fun updateAnalyzedItemPortion(itemId: String, unit: PortionUnit, quantity: Double) {
        val currentList = _detectedFoodItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentList[index]
            val food = old.matchedFood
            val grams = if (food != null) PortionEngine.calculateGrams(food, unit, quantity) else quantity
            val nutrition = if (food != null) PortionEngine.calculateNutrition(food, grams) else CalculatedNutrition(grams, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

            currentList[index] = old.copy(
                portionUnit = unit,
                portionQuantity = quantity,
                calculatedNutrition = nutrition
            )
            _detectedFoodItems.value = currentList
        }
    }

    fun changeAnalyzedItemFood(itemId: String, newFood: FoodItem) {
        val currentList = _detectedFoodItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentList[index]
            val grams = PortionEngine.calculateGrams(newFood, old.portionUnit, old.portionQuantity)
            val nutrition = PortionEngine.calculateNutrition(newFood, grams)

            currentList[index] = old.copy(
                matchedFood = newFood,
                needsClarification = false,
                confidence = 98,
                calculatedNutrition = nutrition
            )
            _detectedFoodItems.value = currentList
        }
    }

    fun saveAllDetectedMeals() {
        val items = _detectedFoodItems.value
        val uriStr = _selectedImageUri.value?.toString()
        val mealType = _selectedMealType.value

        viewModelScope.launch {
            var count = 0
            for (item in items) {
                val food = item.matchedFood ?: continue
                repository.logMeal(
                    food = food,
                    mealType = mealType,
                    portionUnit = item.portionUnit,
                    portionQuantity = item.portionQuantity,
                    photoUri = uriStr,
                    detectedByAi = true,
                    aiConfidence = item.confidence
                )
                count++
            }

            if (count > 0) {
                _saveSuccessMessage.value = "Successfully logged $count food item(s) to $mealType!"
                clearScanner()
                navigateTo(AppDestination.DASHBOARD)
            }
        }
    }

    // Direct manual log meal from database
    fun logMealDirect(
        food: FoodItem,
        mealType: String,
        unit: PortionUnit,
        quantity: Double
    ) {
        viewModelScope.launch {
            repository.logMeal(
                food = food,
                mealType = mealType,
                portionUnit = unit,
                portionQuantity = quantity,
                detectedByAi = false,
                aiConfidence = 100
            )
            _saveSuccessMessage.value = "Added ${food.foodName} to $mealType!"
        }
    }

    fun deleteMeal(record: MealRecord) {
        viewModelScope.launch {
            repository.deleteMealRecord(record)
        }
    }

    fun toggleFoodFavorite(food: FoodItem) {
        viewModelScope.launch {
            repository.toggleFavorite(food.foodId, !food.isFavorite)
        }
    }

    fun addCustomFood(food: FoodItem) {
        viewModelScope.launch {
            repository.insertCustomFood(food.copy(isCustom = true))
            _saveSuccessMessage.value = "Custom food '${food.foodName}' added to database!"
        }
    }

    fun deleteCustomFood(food: FoodItem) {
        viewModelScope.launch {
            repository.deleteFood(food)
        }
    }

    // Profile & Health Tools
    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(profile)
        }
    }

    fun getBmi(profile: UserProfile): BmiResult {
        return HealthCalculators.calculateBmi(profile.heightCm, profile.weightKg)
    }

    fun getBmrTdee(profile: UserProfile): BmrTdeeResult {
        return HealthCalculators.calculateBmrAndTdee(
            profile.gender,
            profile.weightKg,
            profile.heightCm,
            profile.age,
            profile.activityLevel
        )
    }

    // Reminders
    fun saveReminder(reminder: ExerciseReminder) {
        viewModelScope.launch {
            repository.saveReminder(reminder)
        }
    }

    fun toggleReminder(reminder: ExerciseReminder) {
        viewModelScope.launch {
            repository.setReminderEnabled(reminder.id, !reminder.isEnabled)
        }
    }

    fun deleteReminder(reminder: ExerciseReminder) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun testReminder(reminder: ExerciseReminder) {
        viewModelScope.launch {
            repository.testTriggerReminder(reminder)
        }
    }

    fun snoozeReminder(reminderId: Long, snoozeMinutes: Int = 10) {
        viewModelScope.launch {
            val rem = reminders.value.find { it.id == reminderId }
            val title = rem?.title ?: "Exercise Reminder"
            val type = rem?.exerciseType ?: "Morning Workout"
            repository.recordReminderAction(
                reminderId = reminderId,
                title = title,
                exerciseType = type,
                status = "SNOOZED",
                snoozeMinutes = snoozeMinutes,
                notes = "Snoozed for $snoozeMinutes min"
            )
            _activeAlarmTriggered.value = null
        }
    }

    fun markReminderDone(reminderId: Long) {
        viewModelScope.launch {
            val rem = reminders.value.find { it.id == reminderId }
            val title = rem?.title ?: "Exercise Reminder"
            val type = rem?.exerciseType ?: "Morning Workout"
            repository.recordReminderAction(
                reminderId = reminderId,
                title = title,
                exerciseType = type,
                status = "COMPLETED"
            )
            _activeAlarmTriggered.value = null
        }
    }

    fun dismissReminder(reminderId: Long) {
        viewModelScope.launch {
            val rem = reminders.value.find { it.id == reminderId }
            val title = rem?.title ?: "Exercise Reminder"
            val type = rem?.exerciseType ?: "Morning Workout"
            repository.recordReminderAction(
                reminderId = reminderId,
                title = title,
                exerciseType = type,
                status = "DISMISSED"
            )
            _activeAlarmTriggered.value = null
        }
    }

    fun showActiveAlarm(reminder: ExerciseReminder) {
        _activeAlarmTriggered.value = reminder
    }

    fun dismissActiveAlarmDialog() {
        _activeAlarmTriggered.value = null
    }

    fun clearReminderHistory() {
        viewModelScope.launch {
            repository.clearReminderHistory()
        }
    }

    // AI Chat
    fun sendChatMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return

        val userMessage = ChatMessage(sender = "user", text = trimmed)
        val current = _chatMessages.value.toMutableList()
        current.add(userMessage)
        _chatMessages.value = current
        _isChatLoading.value = true

        viewModelScope.launch {
            val history = current.dropLast(1).map { Pair(it.sender, it.text) }
            val answer = repository.askNutritionAssistant(trimmed, history)

            val modelMessage = ChatMessage(sender = "model", text = answer)
            val updated = _chatMessages.value.toMutableList()
            updated.add(modelMessage)
            _chatMessages.value = updated
            _isChatLoading.value = false
        }
    }
}
