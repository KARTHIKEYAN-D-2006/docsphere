package com.docsphere.data.repository

import android.util.Log
import com.docsphere.ai.AiResponse
import com.docsphere.ai.AiRouter
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RetrievedChunk
import com.docsphere.data.model.RoutingDecision
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.docsphere.util.SensitiveDataScrubber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val inMemoryChatManager: InMemoryChatManager,
    private val aiRouter: AiRouter,
    private val documentRepository: DocumentRepository
) {

    suspend fun getFieldValue(documentId: String, fieldName: String): String? = withContext(Dispatchers.IO) {
        try {
            documentRepository.getFieldValue(documentId, fieldName)
        } catch (e: Throwable) {
            Log.e("DocSphereDebug", "[AiRepository] getFieldValue failed: ${e.message}", e)
            null
        }
    }

    /**
     * Stops active generation and frees model resources.
     */
    fun stopGeneration() {
        aiRouter.stopGeneration()
    }

    /**
     * All chat is strictly in-memory (never in Room or any file).
     * No persisted messages exist to purge.
     */
    suspend fun purgeMessagesWithSensitiveData(): Int = 0

    fun isOnline(): Boolean = aiRouter.isOnline()

    private fun isFollowUpQuery(query: String): Boolean {
        val trimmed = query.trim()
        val words = trimmed.split(Regex("""\s+"""))
        if (words.size <= 4) return true
        val lower = trimmed.lowercase(Locale.ROOT)
        val followUpPatterns = listOf(
            Regex("""\b(it|that|this|the second one|the first one|the other one|second one|first one)\b"""),
            Regex("""\b(and the|what about|which one|tell me about|how about)\b"""),
            Regex("""\b(same|both|and pan|and aadhaar|and aadhar)\b""")
        )
        return followUpPatterns.any { it.containsMatchIn(lower) }
    }

    /**
     * Routes a user query to the appropriate AI engine.
     * Records the user message in in-memory state.
     * The caller is responsible for recording the assistant reply.
     */
    suspend fun chat(
        query: String,
        sessionId: String,
        apiKey: String,
        forceOffline: Boolean = false,
        personalIntelligence: Boolean = true
    ): AiResponse = withContext(Dispatchers.IO) {
        val history = inMemoryChatManager.getMessages()

        // Record user message in in-memory chat state
        inMemoryChatManager.addUserMessage(query)

        // If Personal Intelligence is OFF, bypass document retrieval, embeddings, FTS, and DB reads entirely
        if (!personalIntelligence) {
            return@withContext aiRouter.route(
                query = query,
                relevantChunks = emptyList(),
                sourceDocs = emptyList(),
                apiKey = apiKey,
                forceOffline = forceOffline,
                history = history,
                personalIntelligence = false
            )
        }

        // Follow-up query expansion for retrieval keywords
        val prevUserMsg = history.lastOrNull { it.role == MessageRole.USER }?.content
        val searchKeywords = if (isFollowUpQuery(query) && !prevUserMsg.isNullOrBlank()) {
            "$prevUserMsg $query"
        } else {
            query
        }

        // Retrieve relevant document context and top chunks with a 5-second timeout
        val (relevantChunks, relevantDocs) = try {
            kotlinx.coroutines.withTimeout(5000L) {
                val chunks = try {
                    documentRepository.getRelevantChunks(searchKeywords, limit = 3)
                } catch (e: Throwable) {
                    Log.e("DocSphereDebug", "[AiRepository] getRelevantChunks failed: ${e.message}", e)
                    emptyList()
                }

                val docs = try {
                    documentRepository.getRelevantDocs(searchKeywords)
                } catch (e: Throwable) {
                    Log.e("DocSphereDebug", "[AiRepository] getRelevantDocs failed: ${e.message}", e)
                    emptyList()
                }
                chunks to docs
            }
        } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("DocSphereDebug", "[AiRepository] Retrieval timed out after 5000ms")
            return@withContext AiResponse(
                textFlow = AiRouter.streamTextFlow("⏱️ Document search timed out (exceeded 5 seconds). Please try asking again."),
                routingDecision = RoutingDecision.LOCAL_ONLY,
                routingReason = "Search timeout exceeded",
                sourceDocs = emptyList()
            )
        }

        val sourceDocs = if (relevantChunks.isNotEmpty()) {
            val docIds = relevantChunks.map { it.documentId }.distinct()
            val filtered = relevantDocs.filter { it.id in docIds }
            val missingDocs = relevantChunks.filter { c -> filtered.none { it.id == c.documentId } }
                .map { c ->
                    DocumentRoomEntity(
                        id = c.documentId,
                        displayName = c.documentName,
                        sourceType = SourceType.LOCAL_STORAGE,
                        sensitivityTag = SensitivityTag.NONE,
                        extractedText = "",
                        pageCount = 1,
                        mimeType = "application/pdf",
                        fileSizeBytes = 0L,
                        lastModifiedEpochMillis = System.currentTimeMillis(),
                        localUri = null,
                        cloudRef = null
                    )
                }
            (filtered + missingDocs).distinctBy { it.id }.take(3)
        } else {
            emptyList()
        }

        aiRouter.route(
            query = query,
            relevantChunks = relevantChunks,
            sourceDocs = sourceDocs,
            apiKey = apiKey,
            forceOffline = forceOffline,
            history = history,
            personalIntelligence = true
        )
    }

    suspend fun persistAssistantMessage(
        sessionId: String,
        content: String,
        routingDecision: RoutingDecision,
        sourceDocs: List<DocumentRoomEntity> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val citations = sourceDocs.take(3).map { doc ->
            RetrievedChunk(
                documentId = doc.id,
                documentName = doc.displayName,
                pageNumber = null,
                snippet = doc.extractedText.take(200),
                relevanceScore = 1.0f,
                sourceType = doc.sourceType
            )
        }

        val cleanContent = content
            .replace(Regex("""<think>[\s\S]*?</think>"""), "")
            .replace(Regex("""<think>[\s\S]*"""), "")
            .trim()

        inMemoryChatManager.addAssistantMessage(
            content = cleanContent,
            routingDecision = routingDecision,
            citations = citations
        )
    }

    fun observeChatHistory(sessionId: String): Flow<List<ChatMessage>> =
        inMemoryChatManager.messages

    suspend fun clearSession(sessionId: String) {
        inMemoryChatManager.clearChat()
    }

    suspend fun clearAllHistory() {
        inMemoryChatManager.clearChat()
    }
}
