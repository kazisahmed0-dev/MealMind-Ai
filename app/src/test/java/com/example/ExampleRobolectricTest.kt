package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.calculation.HealthCalculators
import com.example.calculation.PortionEngine
import com.example.calculation.PortionUnit
import com.example.data.model.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MealMind AI", appName)
  }

  @Test
  fun `verify portion engine deterministic calculation`() {
    val testFood = FoodItem(
      foodName = "Cooked White Rice",
      category = "Rice & Grains",
      caloriesPer100g = 130.0,
      proteinGPer100g = 2.7,
      carbsGPer100g = 28.2,
      fatGPer100g = 0.3,
      fiberGPer100g = 0.4
    )

    // 150g of rice
    val calculated = PortionEngine.calculateNutrition(testFood, 150.0)
    assertEquals(195.0, calculated.calories, 0.01)
    assertEquals(4.1, calculated.proteinG, 0.01)
    assertEquals(42.3, calculated.carbsG, 0.01)
  }

  @Test
  fun `verify health calculator BMI and BMR`() {
    // Height 170cm, Weight 70kg -> BMI = 70 / (1.7^2) = 24.2
    val bmi = HealthCalculators.calculateBmi(170.0, 70.0)
    assertEquals(24.2, bmi.bmiValue, 0.1)
    assertEquals("Normal Weight", bmi.category)

    val bmr = HealthCalculators.calculateBmrAndTdee(
      gender = "Male",
      weightKg = 70.0,
      heightCm = 170.0,
      age = 25,
      activityLevel = "Moderately Active"
    )
    assertTrue(bmr.bmr > 1500)
    assertTrue(bmr.tdee > bmr.bmr)
  }
}

