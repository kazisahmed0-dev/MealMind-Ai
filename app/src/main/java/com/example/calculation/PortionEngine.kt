package com.example.calculation

import com.example.data.model.FoodItem
import java.math.BigDecimal
import java.math.RoundingMode

data class CalculatedNutrition(
    val portionGrams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double
)

enum class PortionUnit(val displayName: String, val baseGramsMultiplier: Double) {
    GRAMS("grams (g)", 1.0),
    KILOGRAMS("kilograms (kg)", 1000.0),
    MILLILITERS("ml / soup", 1.0),
    PIECES("pieces", 50.0), // Contextual based on food or default
    SLICES("slices", 30.0),
    CUPS("cups", 160.0), // Standard cooked cup ~ 160g
    BOWLS("bowls", 250.0), // Standard bowl ~ 250g
    TABLESPOONS("tablespoons (tbsp)", 15.0),
    TEASPOONS("teaspoons (tsp)", 5.0),
    SERVINGS("servings", 100.0) // Contextual from food.defaultPortionGrams
}

object PortionEngine {

    /**
     * Converts a given unit and quantity into grams.
     * Respects food-specific default serving / piece weight when available.
     */
    fun calculateGrams(
        food: FoodItem,
        unit: PortionUnit,
        quantity: Double
    ): Double {
        if (quantity <= 0.0) return 0.0

        val gramsPerUnit: Double = when (unit) {
            PortionUnit.GRAMS -> 1.0
            PortionUnit.KILOGRAMS -> 1000.0
            PortionUnit.MILLILITERS -> 1.0
            PortionUnit.SERVINGS -> food.defaultPortionGrams
            PortionUnit.PIECES -> {
                // If the default serving is piece-sized, use it, else reasonable default
                if (food.defaultPortionGrams in 20.0..250.0) food.defaultPortionGrams else 60.0
            }
            PortionUnit.SLICES -> {
                if (food.category == "Rice & Grains") 30.0 // bread slice
                else if (food.category == "Snacks & Fast Food") 115.0 // pizza slice
                else 40.0
            }
            PortionUnit.CUPS -> {
                if (food.category == "Beverages & Drinks" || food.category == "Eggs & Dairy") 240.0
                else if (food.category == "Dal & Lentils") 200.0
                else 160.0
            }
            PortionUnit.BOWLS -> {
                if (food.category == "Rice & Grains") 200.0
                else 250.0
            }
            PortionUnit.TABLESPOONS -> 15.0
            PortionUnit.TEASPOONS -> 5.0
        }

        return roundToOneDecimal(quantity * gramsPerUnit)
    }

    /**
     * Deterministic calculation:
     * Calories = (Calories per 100g * portion in grams) / 100
     */
    fun calculateNutrition(food: FoodItem, grams: Double): CalculatedNutrition {
        if (grams <= 0.0) {
            return CalculatedNutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val factor = grams / 100.0

        val calories = roundToOneDecimal(food.caloriesPer100g * factor)
        val protein = roundToOneDecimal(food.proteinGPer100g * factor)
        val carbs = roundToOneDecimal(food.carbsGPer100g * factor)
        val fat = roundToOneDecimal(food.fatGPer100g * factor)
        val fiber = roundToOneDecimal(food.fiberGPer100g * factor)
        val sugar = roundToOneDecimal(food.sugarGPer100g * factor)
        val sodium = roundToOneDecimal(food.sodiumMgPer100g * factor)

        return CalculatedNutrition(
            portionGrams = roundToOneDecimal(grams),
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
            sugarG = sugar,
            sodiumMg = sodium
        )
    }

    private fun roundToOneDecimal(value: Double): Double {
        return BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    }
}
