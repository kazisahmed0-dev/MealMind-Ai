package com.example.network.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<ContentItem>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    @Json(name = "role") val role: String = "user",
    @Json(name = "parts") val parts: List<PartItem>
)

@JsonClass(generateAdapter = true)
data class PartItem(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float = 0.2f,
    @Json(name = "topP") val topP: Float = 0.8f,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<CandidateItem>? = null
)

@JsonClass(generateAdapter = true)
data class CandidateItem(
    @Json(name = "content") val content: ContentResponse? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "parts") val parts: List<PartResponse>? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    @Json(name = "text") val text: String? = null
)

/**
 * Structured parsed response from AI Vision for food photo
 */
data class VisionFoodAnalysisResult(
    val isFood: Boolean,
    val imageQualityOk: Boolean,
    val rejectionMessage: String? = null,
    val items: List<DetectedFoodItem> = emptyList()
)

data class DetectedFoodItem(
    val detectedName: String,
    val estimatedGrams: Double,
    val confidence: Int, // 0 - 100
    val preparation: String? = null,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val clarificationOptions: List<String> = emptyList()
)
