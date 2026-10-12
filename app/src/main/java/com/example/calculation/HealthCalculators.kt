package com.example.calculation

import java.math.BigDecimal
import java.math.RoundingMode

data class BmiResult(
    val bmiValue: Double,
    val category: String,
    val description: String,
    val healthyWeightRangeMin: Double,
    val healthyWeightRangeMax: Double
)

data class BmrTdeeResult(
    val bmr: Double,
    val tdee: Double,
    val weightLossCalories: Int,
    val maintainCalories: Int,
    val weightGainCalories: Int,
    val explanation: String
)

object HealthCalculators {

    fun calculateBmi(heightCm: Double, weightKg: Double): BmiResult {
        if (heightCm <= 0.0 || weightKg <= 0.0) {
            return BmiResult(0.0, "Invalid", "Please provide valid height and weight.", 0.0, 0.0)
        }

        val heightM = heightCm / 100.0
        val rawBmi = weightKg / (heightM * heightM)
        val bmi = BigDecimal(rawBmi).setScale(1, RoundingMode.HALF_UP).toDouble()

        val minHealthyWeight = BigDecimal(18.5 * heightM * heightM).setScale(1, RoundingMode.HALF_UP).toDouble()
        val maxHealthyWeight = BigDecimal(24.9 * heightM * heightM).setScale(1, RoundingMode.HALF_UP).toDouble()

        val (category, desc) = when {
            bmi < 18.5 -> Pair("Underweight", "Your BMI is below normal. Focus on nutrient-dense foods, lean protein, and healthy fats.")
            bmi in 18.5..24.9 -> Pair("Normal Weight", "Great job! Your BMI is within the healthy recommended range. Maintain balanced meals and activity.")
            bmi in 25.0..29.9 -> Pair("Overweight", "Your BMI is slightly elevated. A balanced calorie deficit and regular exercise can help reach target weight.")
            else -> Pair("Obesity", "Your BMI is high. Consult a healthcare provider or dietitian to develop a safe, structured wellness plan.")
        }

        return BmiResult(
            bmiValue = bmi,
            category = category,
            description = desc,
            healthyWeightRangeMin = minHealthyWeight,
            healthyWeightRangeMax = maxHealthyWeight
        )
    }

    fun calculateBmrAndTdee(
        gender: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        activityLevel: String
    ): BmrTdeeResult {
        if (weightKg <= 0 || heightCm <= 0 || age <= 0) {
            return BmrTdeeResult(0.0, 0.0, 0, 0, 0, "Invalid parameters")
        }

        // Mifflin-St Jeor formula
        val isMale = gender.equals("Male", ignoreCase = true)
        val baseBmr = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + (if (isMale) 5.0 else -161.0)
        val bmr = BigDecimal(baseBmr).setScale(0, RoundingMode.HALF_UP).toDouble()

        val activityMultiplier = when (activityLevel.lowercase()) {
            "sedentary" -> 1.2
            "lightly active" -> 1.375
            "moderately active" -> 1.55
            "very active" -> 1.725
            "extra active" -> 1.9
            else -> 1.375
        }

        val rawTdee = bmr * activityMultiplier
        val maintain = rawTdee.toInt()
        val loss = (maintain - 500).coerceAtLeast(1200)
        val gain = maintain + 500

        val explanation = "Your Basal Metabolic Rate (BMR) is the calories your body burns at rest. Your Total Daily Energy Expenditure (TDEE) factors in your $activityLevel lifestyle."

        return BmrTdeeResult(
            bmr = bmr,
            tdee = rawTdee,
            weightLossCalories = loss,
            maintainCalories = maintain,
            weightGainCalories = gain,
            explanation = explanation
        )
    }
}
