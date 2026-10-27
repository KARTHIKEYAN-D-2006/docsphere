package com.docsphere.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.docsphere.ai.offline.GeminiNanoEngine
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.online.GeminiApiEngine
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RoutingDecision
import com.docsphere.data.repository.DocumentChunk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AiResponse(
    val textFlow: Flow<String>,
    val routingDecision: RoutingDecision,
    val routingReason: String,
    val sourceDocs: List<DocumentRoomEntity> = emptyList(),
    val promptContext: String = ""
)

@Singleton
class AiRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nanoEngine: GeminiNanoEngine,
    private val geminiApiEngine: GeminiApiEngine,
    private val modelManager: com.docsphere.ai.offline.OfflineModelManager
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun stopGeneration() {
        nanoEngine.cancelInference()
    }

    suspend fun route(
        query: String,
        relevantChunks: List<DocumentChunk> = emptyList(),
        sourceDocs: List<DocumentRoomEntity> = emptyList(),
        apiKey: String,
        forceOffline: Boolean = false,
        history: List<ChatMessage> = emptyList(),
        personalIntelligence: Boolean = true
    ): AiResponse = withContext(Dispatchers.IO) {
        // Build context from relevant chunks if personal intelligence is enabled
        val topChunks = if (personalIntelligence) relevantChunks.take(3) else emptyList()
        val hasContext = personalIntelligence && topChunks.isNotEmpty()

        val contextBlock = if (hasContext) {
            val excerpts = topChunks.joinToString("\n---\n") { chunk ->
                "[${chunk.documentName}]\n${chunk.text}"
            }
            "Here are excerpts from the user's own files (they own this data; showing it to them is fine):\n$excerpts\n\nUse them if they help answer the question and quote exact values. If they do not contain the answer, say you could not find it in the files; do not say 'I checked the context'. Question: $query"
        } else {
            ""
        }

        val effectiveSourceDocs = if (hasContext) {
            sourceDocs.filter { doc -> topChunks.any { it.documentId == doc.id } }.take(3)
        } else {
            emptyList()
        }

        val historyTurns = formatHistoryTurns(history)
        // Item 1: NO MODEL AUTO-SWAP. Always run exactly the model the user selected.
        val activeModel = modelManager.selectedModel.value

        val online = isOnline() && apiKey.isNotBlank() && !forceOffline

        if (online) {
            val apiFlow = geminiApiEngine.chat(
                apiKey = apiKey,
                systemPrompt = SYSTEM_PROMPT_NO_CONTEXT,
                userMessage = if (hasContext) contextBlock else query,
                contextChunks = emptyList(),
                history = history,
                offlineFallbackFlow = nanoEngine.infer(
                    systemPrompt = SYSTEM_PROMPT_NO_CONTEXT,
                    userQuery = query,
                    contextChunks = if (contextBlock.isNotBlank()) listOf(contextBlock) else emptyList(),
                    historyTurns = historyTurns,
                    targetModel = activeModel,
                    personalIntelligence = personalIntelligence
                )
            )
            AiResponse(
                textFlow = apiFlow,
                routingDecision = if (hasContext) RoutingDecision.FUSED else RoutingDecision.CLOUD_BOOSTED,
                routingReason = if (hasContext) "Online • Gemini (${effectiveSourceDocs.size} Sources)" else "Online • Gemini",
                sourceDocs = effectiveSourceDocs,
                promptContext = contextBlock
            )
        } else {
            val offlineFlow = nanoEngine.infer(
                systemPrompt = SYSTEM_PROMPT_NO_CONTEXT,
                userQuery = query,
                contextChunks = if (contextBlock.isNotBlank()) listOf(contextBlock) else emptyList(),
                historyTurns = historyTurns,
                targetModel = activeModel,
                personalIntelligence = personalIntelligence
            )
            AiResponse(
                textFlow = offlineFlow,
                routingDecision = if (hasContext) RoutingDecision.FUSED else RoutingDecision.LOCAL_ONLY,
                routingReason = if (hasContext) "${activeModel.displayName} (${effectiveSourceDocs.size} Sources)" else activeModel.displayName,
                sourceDocs = effectiveSourceDocs,
                promptContext = contextBlock
            )
        }
    }

    companion object {
        const val SYSTEM_PROMPT_NO_CONTEXT =
            "You are DocSphere AI, a friendly assistant on the user's phone. Answer helpfully and briefly."

        const val DOCSPHERE_SYSTEM_PROMPT = SYSTEM_PROMPT_NO_CONTEXT

        fun formatHistoryTurns(history: List<ChatMessage>, maxChars: Int = 2000): List<Pair<String, String>> {
            val cleanMessages = mutableListOf<Pair<String, String>>()
            for (msg in history) {
                if (msg.role == MessageRole.USER) {
                    val content = msg.content.trim()
                    if (content.isNotBlank()) {
                        cleanMessages.add("User" to content)
                    }
                } else if (msg.role == MessageRole.ASSISTANT) {
                    val raw = msg.content.trim()
                    val isError = raw.startsWith("❌") || raw.startsWith("⚠️") || raw.startsWith("⏱️") || raw.startsWith("Model error:") || raw.startsWith("⏹️")
                    val lower = raw.lowercase(java.util.Locale.ROOT)
                    val isNoInfo = lower.contains("could not find") ||
                                   lower.contains("couldn't find") ||
                                   lower.contains("no information") ||
                                   lower.contains("not found in") ||
                                   lower.contains("cannot find") ||
                                   lower.contains("can't find") ||
                                   lower.contains("did not find") ||
                                   lower.contains("didn't find") ||
                                   lower.contains("checked the files")

                    if (!isError && !isNoInfo && raw.isNotBlank()) {
                        val cleanContent = raw.substringBefore("\nSources:").substringBefore("\n**Sources:**").trim()
                        if (cleanContent.isNotBlank()) {
                            cleanMessages.add("DocSphere AI" to cleanContent)
                        }
                    }
                }
            }
            // Send only the last 2 turns (up to 4 messages: User, Assistant, User, Assistant)
            val lastTurns = cleanMessages.takeLast(4).toMutableList()
            while (lastTurns.isNotEmpty() && lastTurns.sumOf { it.first.length + it.second.length + 4 } > maxChars) {
                lastTurns.removeAt(0)
            }
            return lastTurns
        }

        fun streamTextFlow(text: String): Flow<String> = kotlinx.coroutines.flow.flow {
            val words = text.split(" ")
            val chunkSize = 3
            for (i in words.indices step chunkSize) {
                val chunk = words.subList(i, minOf(i + chunkSize, words.size)).joinToString(" ") + " "
                emit(chunk)
                kotlinx.coroutines.delay(12)
            }
        }
    }
}
