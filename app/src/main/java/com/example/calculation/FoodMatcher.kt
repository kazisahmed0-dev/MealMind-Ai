package com.example.calculation

import com.example.data.dao.FoodDao
import com.example.data.model.FoodItem
import kotlinx.coroutines.flow.first

data class MatchResult(
    val matchedFood: FoodItem,
    val matchType: MatchType,
    val matchScore: Int // 0 to 100
)

enum class MatchType {
    EXACT,
    NORMALIZED,
    ALIAS,
    FUZZY,
    FALLBACK
}

object FoodMatcher {

    /**
     * Attempts to match a detected food name against the authoritative Room database.
     */
    suspend fun matchFood(
        detectedName: String,
        foodDao: FoodDao
    ): Pair<FoodItem?, List<FoodItem>> {
        val cleanDetected = detectedName.trim()
        if (cleanDetected.isBlank()) return Pair(null, emptyList())

        // 1. Direct exact match
        val exact = foodDao.getFoodByName(cleanDetected)
        if (exact != null) {
            return Pair(exact, emptyList())
        }

        // 2. Candidates from database containing words
        val candidates = foodDao.findCandidates(cleanDetected)
        if (candidates.isNotEmpty()) {
            val scored = candidates.map { food ->
                val score = calculateSimilarity(cleanDetected, food)
                MatchResult(food, MatchType.FUZZY, score)
            }.sortedByDescending { it.matchScore }

            val best = scored.firstOrNull()
            if (best != null && best.matchScore >= 50) {
                return Pair(best.matchedFood, scored.drop(1).map { it.matchedFood })
            }
        }

        // 3. Fallback search by key keywords (e.g. "Rice", "Chicken", "Egg", "Dal", "Beef", "Fish")
        val keywords = cleanDetected.split(" ", ",", "-")
            .map { it.trim().lowercase() }
            .filter { it.length > 2 && it !in setOf("cooked", "fresh", "plate", "bowl", "with", "and", "fried", "plain", "steamed") }

        for (keyword in keywords) {
            val kwCandidates = foodDao.findCandidates(keyword)
            if (kwCandidates.isNotEmpty()) {
                val best = kwCandidates.first()
                return Pair(best, kwCandidates.drop(1))
            }
        }

        // 4. Return top generic foods as suggestions if nothing matched
        val allFoods = foodDao.getAllFoods().first().take(5)
        return Pair(null, allFoods)
    }

    private fun calculateSimilarity(detected: String, food: FoodItem): Int {
        val d = detected.lowercase().trim()
        val fName = food.foodName.lowercase().trim()
        val fAlt = food.alternativeNames.lowercase().trim()

        if (d == fName) return 100
        if (fAlt.contains(d)) return 95
        if (fName.contains(d) || d.contains(fName)) return 90

        val dWords = d.split(" ").filter { it.length > 2 }
        val fWords = (fName + " " + fAlt).split(" ", ",").filter { it.length > 2 }

        var common = 0
        for (w in dWords) {
            if (fWords.any { it.contains(w) || w.contains(it) }) {
                common++
            }
        }

        return if (dWords.isNotEmpty()) {
            (common.toDouble() / dWords.size * 80).toInt()
        } else {
            0
        }
    }
}
