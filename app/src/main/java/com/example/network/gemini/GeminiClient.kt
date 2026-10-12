package com.example.network.gemini

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Inspects the photo using Gemini 2.5 Flash Vision.
     * Returns a structured VisionFoodAnalysisResult containing detected food items,
     * estimated portions, confidence ratings, and clarification questions if ambiguous.
     */
    suspend fun analyzeFoodPhoto(base64Image: String, mimeType: String = "image/jpeg"): VisionFoodAnalysisResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return VisionFoodAnalysisResult(
                isFood = false,
                imageQualityOk = false,
                rejectionMessage = "Gemini API key is not configured. Please add your key in AI Studio Secrets or .env file."
            )
        }

        val prompt = """
            You are the food recognition vision engine for MealMind AI.
            Inspect the provided food image strictly.
            
            Return a pure JSON response matching this EXACT schema:
            {
              "isFood": boolean, // false if photo is not food/drink (e.g. document, furniture, shoe, pet)
              "imageQualityOk": boolean, // false if extremely blurry, too dark, or completely unrecognizable
              "rejectionMessage": string or null, // reason if not food or unusable photo
              "items": [
                {
                  "detectedName": string, // Standardized common food name (e.g., "Cooked White Rice", "Chicken Curry Bengali", "Alu Bhorta", "Plain Naan", "Masoor Dal")
                  "estimatedGrams": number, // Realistic visual weight in grams for this portion on the plate (e.g., 180 for rice, 150 for curry)
                  "confidence": integer, // 0 to 100 confidence score
                  "preparation": string, // e.g. "Steamed", "Deep Fried", "Spiced Gravy", "Raw"
                  "needsClarification": boolean, // true if ambiguous (e.g. mutton vs beef, or portion obscured)
                  "clarificationQuestion": string or null, // e.g. "Is this beef or mutton?", "Is the curry spicy mustard or onion based?"
                  "clarificationOptions": [string] // 2-3 quick choices, e.g. ["Beef Bhuna", "Mutton Curry"]
                }
              ]
            }
            
            Rules:
            1. If the plate has multiple foods (e.g. Rice + Chicken + Dal + Salad), return ALL items separately in the "items" array!
            2. Never invent random nutritional calories; your task is ONLY to detect the food names, portions, and confidence.
            3. Do not include markdown ticks or anything outside the JSON object.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                ContentItem(
                    parts = listOf(
                        PartItem(text = prompt),
                        PartItem(inlineData = InlineData(mimeType = mimeType, data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                topP = 0.8f,
                responseMimeType = "application/json"
            )
        )

        val response = service.generateContent(apiKey = apiKey, request = request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        return parseVisionJson(text)
    }

    /**
     * Nutrition Chat Assistant with Gemini
     */
    suspend fun askNutritionAssistant(userQuery: String, conversationHistory: List<Pair<String, String>> = emptyList()): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return "Please provide your Gemini API key in the Secrets panel or .env file to enable the AI Nutrition Assistant."
        }

        val systemInstruction = """
            You are MealMind AI's Friendly & Educational Nutrition Assistant.
            
            Key Rules:
            1. Provide clear, supportive, scientifically sound nutrition and healthy eating guidance.
            2. Familiar with Bangladeshi, South Asian, Asian, and global cuisines.
            3. Clarify concepts like calories, protein, carbs, healthy fats, fiber, vitamins, and hydration.
            4. MEDICAL DISCLAIMER MANDATE: You are an educational tool and NOT a doctor or medical professional. Never diagnose medical conditions or prescribe treatments. Always advise consulting a qualified physician or registered dietitian for medical inquiries.
            5. Keep answers concise, helpful, and formatted with clean bullet points.
        """.trimIndent()

        val contents = mutableListOf<ContentItem>()
        // Add conversation turns
        for ((userMsg, assistantMsg) in conversationHistory.takeLast(4)) {
            contents.add(ContentItem(role = "user", parts = listOf(PartItem(text = userMsg))))
            contents.add(ContentItem(role = "model", parts = listOf(PartItem(text = assistantMsg))))
        }
        contents.add(ContentItem(role = "user", parts = listOf(PartItem(text = "$systemInstruction\n\nUser Question: $userQuery"))))

        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                topP = 0.9f
            )
        )

        val response = service.generateContent(apiKey = apiKey, request = request)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: "Sorry, I couldn't generate a response. Please check your network connection and try again."
    }

    private fun parseVisionJson(rawJson: String): VisionFoodAnalysisResult {
        try {
            val cleaned = rawJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleaned)
            val isFood = json.optBoolean("isFood", true)
            val qualityOk = json.optBoolean("imageQualityOk", true)
            val rejection = json.optString("rejectionMessage", null)

            val items = mutableListOf<DetectedFoodItem>()
            val jsonArray = json.optJSONArray("items")
            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("detectedName", "Food Item")
                    val grams = obj.optDouble("estimatedGrams", 150.0)
                    val confidence = obj.optInt("confidence", 85)
                    val prep = obj.optString("preparation", "")
                    val needsClarification = obj.optBoolean("needsClarification", false)
                    val question = obj.optString("clarificationQuestion", "")
                    val opts = mutableListOf<String>()
                    val optsArr = obj.optJSONArray("clarificationOptions")
                    if (optsArr != null) {
                        for (j in 0 until optsArr.length()) {
                            opts.add(optsArr.getString(j))
                        }
                    }

                    items.add(
                        DetectedFoodItem(
                            detectedName = name,
                            estimatedGrams = grams,
                            confidence = confidence,
                            preparation = prep.ifBlank { null },
                            needsClarification = needsClarification || confidence < 75,
                            clarificationQuestion = question.ifBlank { null },
                            clarificationOptions = opts
                        )
                    )
                }
            }

            return VisionFoodAnalysisResult(
                isFood = isFood,
                imageQualityOk = qualityOk,
                rejectionMessage = rejection,
                items = items
            )
        } catch (e: Exception) {
            return VisionFoodAnalysisResult(
                isFood = true,
                imageQualityOk = true,
                items = listOf(
                    DetectedFoodItem(
                        detectedName = "Mixed Food Plate",
                        estimatedGrams = 200.0,
                        confidence = 65,
                        needsClarification = true,
                        clarificationQuestion = "Could you confirm what food this is?",
                        clarificationOptions = listOf("White Rice", "Chicken Curry", "Mixed Vegetables")
                    )
                )
            )
        }
    }
}
