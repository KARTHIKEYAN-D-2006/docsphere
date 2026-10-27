package com.docsphere.ai.online

import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiRequest(
    val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig = GenerationConfig()
) {
    data class Content(val role: String, val parts: List<Part>)
    data class Part(val text: String)
    data class GenerationConfig(
        val temperature: Float = 0.7f,
        val maxOutputTokens: Int = 2048,
        val topK: Int = 40,
        val topP: Float = 0.95f
    )
}

/**
 * Calls the Google Gemini REST API with dynamic model discovery and automatic fallback.
 * Uses official active Google AI models (gemini-3.8-flash, gemini-flash-latest, etc.).
 * If cloud API is unreachable or fails, seamlessly delegates to on-device offline fallback.
 */
@Singleton
class GeminiApiEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        // Active Google AI Studio Gemini models in order of performance and availability
        val CANDIDATE_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
        private const val URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
        private const val MODELS_LIST_URL =
            "https://generativelanguage.googleapis.com/v1beta/models?key=%s"
    }

    // Cached active model that previously succeeded
    @Volatile
    private var activeModel: String = CANDIDATE_MODELS.first()

    @Volatile
    private var discoveredModels: List<String> = emptyList()

    fun chat(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        contextChunks: List<String> = emptyList(),
        history: List<ChatMessage> = emptyList(),
        offlineFallbackFlow: Flow<String>? = null
    ): Flow<String> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            if (offlineFallbackFlow != null) {
                offlineFallbackFlow.collect { emit(it) }
            } else {
                emit("Please configure a Gemini API key in Settings or switch to Offline Mode.")
            }
            return@flow
        }

        // Build context-enriched prompt
        val contextBlock = if (contextChunks.isNotEmpty()) {
            "\n\n**Relevant document excerpts:**\n" +
            contextChunks.take(5).mapIndexed { i, chunk -> "[${i+1}] $chunk" }.joinToString("\n\n")
        } else ""

        val fullUserMessage = "$userMessage$contextBlock"

        val contentsArray = JSONArray().apply {
            if (systemPrompt.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", "Understood. I am DOCSPHERE AI, ready to help.")))
                })
            }
            // Append multi-turn chat history (last 4 turns, max ~500 tokens total, oldest dropped first - Item 3.a)
            val recentTurns = com.docsphere.ai.AiRouter.formatHistoryTurns(history)
            recentTurns.forEach { (role, text) ->
                put(JSONObject().apply {
                    put("role", if (role == "User") "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", fullUserMessage)))
            })
        }

        val jsonBody = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                // Cap answers at 300 tokens for document questions
                put("maxOutputTokens", if (contextChunks.isNotEmpty()) 300 else 2048)
                put("topK", 40)
                put("topP", 0.95)
            })
        }.toString()

        // Assemble model candidates: activeModel first, then discovered models from API, then hardcoded candidates
        val modelsToTry = mutableListOf<String>()
        modelsToTry.add(activeModel)
        if (discoveredModels.isNotEmpty()) {
            modelsToTry.addAll(discoveredModels)
        }
        modelsToTry.addAll(CANDIDATE_MODELS)
        val uniqueModels = modelsToTry.distinct()

        var lastError = ""
        var success = false

        for (modelName in uniqueModels) {
            val url = URL_TEMPLATE.format(modelName) + "?key=$trimmedKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    activeModel = modelName
                    success = true

                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null) {
                            val text = (0 until parts.length())
                                .map { parts.getJSONObject(it).optString("text", "") }
                                .joinToString("")
                            val words = text.split(" ")
                            val chunkSize = 4
                            for (i in words.indices step chunkSize) {
                                val chunk = words.subList(i, minOf(i + chunkSize, words.size)).joinToString(" ") + " "
                                emit(chunk)
                                kotlinx.coroutines.delay(10)
                            }
                        }
                    } else {
                        emit("No response generated.")
                    }
                    break
                } else {
                    val errorDetail = try {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message", "") ?: responseBody
                    } catch (_: Exception) { responseBody }
                    lastError = "API Error ${response.code} ($modelName): $errorDetail"

                    // If 404, query model list to find actual available models for this key
                    if (response.code == 404 && discoveredModels.isEmpty()) {
                        val liveModels = queryAvailableModels(trimmedKey)
                        if (liveModels.isNotEmpty()) {
                            discoveredModels = liveModels
                        }
                    }
                    continue
                }
            } catch (e: Exception) {
                lastError = "Network error: ${e.message ?: "Unknown error"}"
                break
            }
        }

        // If online failed, seamlessly fallback to on-device AI without disruptive banners
        if (!success) {
            if (offlineFallbackFlow != null) {
                offlineFallbackFlow.collect { emit(it) }
            } else {
                emit("Unable to complete request. Please verify your connection or switch to Offline Mode.")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Queries Google's ModelService.ListModels endpoint to dynamically discover
     * available models for the given API key.
     */
    private fun queryAvailableModels(apiKey: String): List<String> {
        return try {
            val url = MODELS_LIST_URL.format(apiKey)
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val array = json.optJSONArray("models") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val m = array.getJSONObject(i)
                    val name = m.optString("name", "").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    val hasGenerate = (0 until (methods?.length() ?: 0)).any {
                        methods?.getString(it) == "generateContent"
                    }
                    if (hasGenerate && name.contains("gemini") && name.contains("flash") && !name.contains("pro")) {
                        list.add(name)
                    }
                }
                list.sortedWith(
                    compareByDescending<String> { it == "gemini-3.5-flash" }
                        .thenByDescending { it == "gemini-3.8-flash" }
                        .thenByDescending { it.contains("3.5-flash") }
                        .thenByDescending { it.contains("3.8-flash") }
                        .thenByDescending { it.contains("flash-lite") }
                        .thenByDescending { it.contains("flash") }
                )
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
