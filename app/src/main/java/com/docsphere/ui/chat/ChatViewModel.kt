package com.docsphere.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsphere.ai.InferenceCoordinator
import com.docsphere.ai.offline.GeminiNanoEngine
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.offline.ModelDownloadState
import com.docsphere.ai.offline.OfflineModelManager
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RoutingDecision
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.AiRepository
import com.docsphere.data.repository.InMemoryChatManager
import com.docsphere.util.SensitiveDataScrubber
import com.docsphere.ai.offline.ModelConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val isOnline: Boolean = false,
    val forceOffline: Boolean = false,
    val sessionId: String = "",
    val routingDecision: RoutingDecision = RoutingDecision.LOCAL_ONLY,
    val routingReason: String = "Found in your documents",
    val streamingMessage: String? = null,
    val selectedModel: LlmModel = LlmModel.BUILT_IN,
    val allModelStates: Map<LlmModel, ModelDownloadState> = emptyMap(),
    val showFullSensitiveValues: Boolean = true,
    val streamingSources: List<com.docsphere.data.model.RetrievedChunk> = emptyList(),
    val isModelLoading: Boolean = false,
    val modelLoadingMessage: String? = null,
    val isWaitingForModel: Boolean = false,
    val gpuFallbackNotice: String? = null,
    val isPersonalIntelligence: Boolean = true,
    val currentModelConfig: ModelConfig = ModelConfig()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val documentRepository: com.docsphere.data.repository.DocumentRepository,
    private val preferences: DocSpherePreferences,
    private val offlineModelManager: OfflineModelManager,
    private val nanoEngine: GeminiNanoEngine,
    private val inMemoryChatManager: InMemoryChatManager
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _routingDecision = MutableStateFlow(RoutingDecision.LOCAL_ONLY)
    private val _routingReason = MutableStateFlow("Found in your documents")
    private val _streamingMessage = MutableStateFlow<String?>(null)
    private val _streamingSources = MutableStateFlow<List<com.docsphere.data.model.RetrievedChunk>>(emptyList())
    private val _isWaitingForModel = MutableStateFlow(false)
    private val _responseTimes = MutableStateFlow<Map<String, Long>>(emptyMap())

    val currentModelConfig: StateFlow<ModelConfig> = offlineModelManager.selectedModel
        .flatMapLatest { model -> preferences.getModelConfig(model) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelConfig())

    init {
        viewModelScope.launch {
            offlineModelManager.initialize()
            // Warm-up on-device engine on background thread
            nanoEngine.warmup()
        }
        viewModelScope.launch {
            preferences.personalIntelligence.collect { enabled ->
                InferenceCoordinator.setPersonalIntelligencePaused(!enabled)
            }
        }
    }

    private val _settingsAndStatus = combine(
        preferences.forceOffline,
        _routingDecision,
        preferences.showFullSensitiveValues,
        preferences.personalIntelligence,
        currentModelConfig
    ) { forceOffline, routing, showFullSensitiveValues, personalIntelligence, modelConfig ->
        object {
            val forceOffline = forceOffline
            val routing = routing
            val showFullSensitiveValues = showFullSensitiveValues
            val personalIntelligence = personalIntelligence
            val modelConfig = modelConfig
        }
    }

    private val _messagesAndSettings = combine(
        inMemoryChatManager.messages,
        _inputText,
        _isLoading,
        _responseTimes,
        _settingsAndStatus
    ) { messages, input, loading, responseTimes, settings ->
        val enriched = messages.map { msg ->
            val time = responseTimes[msg.id]
            if (time != null) msg.copy(responseTimeMs = time) else msg
        }
        object {
            val messages = enriched
            val input = input
            val loading = loading
            val forceOffline = settings.forceOffline
            val routing = settings.routing
            val showFullSensitiveValues = settings.showFullSensitiveValues
            val personalIntelligence = settings.personalIntelligence
            val modelConfig = settings.modelConfig
        }
    }

    private val _engineState = combine(
        nanoEngine.isModelLoading,
        nanoEngine.modelLoadingMessage,
        nanoEngine.gpuFallbackNotice,
        _isWaitingForModel
    ) { isModelLoading, modelLoadingMsg, gpuNotice, isWaitingForModel ->
        object {
            val isModelLoading = isModelLoading
            val modelLoadingMsg = modelLoadingMsg
            val gpuNotice = gpuNotice
            val isWaitingForModel = isWaitingForModel
        }
    }

    private val _modelSelectionState = combine(
        offlineModelManager.selectedModel,
        offlineModelManager.allModelStates
    ) { selectedModel, allModels ->
        Pair(selectedModel, allModels)
    }

    private val _chatTransientState = combine(
        _routingReason,
        _streamingMessage,
        _streamingSources,
        inMemoryChatManager.sessionId
    ) { reason, streaming, sources, sessionId ->
        object {
            val reason = reason
            val streaming = streaming
            val sources = sources
            val sessionId = sessionId
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _messagesAndSettings,
        _chatTransientState,
        _modelSelectionState,
        _engineState
    ) { bundle, transient, (selectedModel, allModels), engineBundle ->
        ChatUiState(
            messages = bundle.messages,
            inputText = bundle.input,
            isLoading = bundle.loading,
            isGenerating = bundle.loading,
            isOnline = aiRepository.isOnline(),
            forceOffline = bundle.forceOffline,
            sessionId = transient.sessionId,
            routingDecision = bundle.routing,
            routingReason = transient.reason,
            streamingMessage = transient.streaming,
            streamingSources = transient.sources,
            selectedModel = selectedModel,
            allModelStates = allModels,
            showFullSensitiveValues = bundle.showFullSensitiveValues,
            isModelLoading = engineBundle.isModelLoading,
            modelLoadingMessage = engineBundle.modelLoadingMsg,
            isWaitingForModel = engineBundle.isWaitingForModel,
            gpuFallbackNotice = engineBundle.gpuNotice,
            isPersonalIntelligence = bundle.personalIntelligence,
            currentModelConfig = bundle.modelConfig
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    private var generationJob: Job? = null

    fun onInputChange(text: String) { _inputText.value = text }

    fun dismissGpuNotice() {
        nanoEngine.dismissGpuNotice()
    }

    fun cancelModelLoading() {
        nanoEngine.cancelLoading()
        _isWaitingForModel.value = false
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        aiRepository.stopGeneration()
        InferenceCoordinator.setInferring(false)
        InferenceCoordinator.setChatActive(false)
        _isLoading.value = false
        _isWaitingForModel.value = false
        _streamingMessage.value = null
        _streamingSources.value = emptyList()
    }

    fun sendMessage() {
        val query = _inputText.value.trim()
        if (query.isBlank() || _isLoading.value) return

        _inputText.value = ""
        _isLoading.value = true
        _streamingMessage.value = null

        generationJob = viewModelScope.launch {
            val queryStartTime = System.currentTimeMillis()
            val apiKey = preferences.geminiApiKey.first()
            val forceOffline = preferences.forceOffline.first()
            val sessionId = inMemoryChatManager.sessionId.value
            val sb = StringBuilder()
            var responseObj: com.docsphere.ai.AiResponse? = null
            var lastChangeTime = System.currentTimeMillis()

            try {
                // If model is currently loading, queue the message and show as waiting
                if (nanoEngine.isModelLoading.value) {
                    _isWaitingForModel.value = true
                    try {
                        nanoEngine.isModelLoading.first { !it }
                    } catch (_: Throwable) {}
                    _isWaitingForModel.value = false
                }

                InferenceCoordinator.setChatActive(true)
                InferenceCoordinator.setInferring(true)

                val personalIntelligence = preferences.personalIntelligence.first()

                val response = aiRepository.chat(
                    query = query,
                    sessionId = sessionId,
                    apiKey = apiKey,
                    forceOffline = forceOffline,
                    personalIntelligence = personalIntelligence
                )
                responseObj = response

                _routingDecision.value = response.routingDecision
                _routingReason.value = response.routingReason
                _streamingSources.value = if (personalIntelligence) {
                    response.sourceDocs.take(3).map { doc ->
                        com.docsphere.data.model.RetrievedChunk(
                            documentId = doc.id,
                            documentName = doc.displayName,
                            pageNumber = null,
                            snippet = doc.extractedText.take(200),
                            relevanceScore = 1.0f,
                            sourceType = doc.sourceType
                        )
                    }
                } else {
                    emptyList()
                }

                var firstChunk = true

                // Requirement C.4: Safety net: monitor for inactivity (10s with no text change while collector is running)
                val safetyNetJob = launch {
                    while (isActive) {
                        delay(1000)
                        val now = System.currentTimeMillis()
                        if (sb.isNotEmpty() && (now - lastChangeTime > 10_000L)) {
                            Log.w("DocSphereDebug", "Safety net triggered in ChatViewModel: 10s inactivity detected. Finalizing generation.")
                            nanoEngine.cancelInference()
                            break
                        }
                    }
                }

                try {
                    var lastEmitTime = 0L
                    response.textFlow.collect { chunk ->
                        if (firstChunk) {
                            firstChunk = false
                            _streamingMessage.value = ""
                            lastEmitTime = System.currentTimeMillis()
                        }
                        sb.append(chunk)
                        val now = System.currentTimeMillis()
                        lastChangeTime = now
                        if (now - lastEmitTime >= 75L) {
                            lastEmitTime = now
                            _streamingMessage.value = sb.toString()
                        }
                    }
                    _streamingMessage.value = sb.toString()
                } finally {
                    safetyNetJob.cancel()
                }

            } catch (c: CancellationException) {
                aiRepository.stopGeneration()
                val partial = sb.toString()
                val message = if (partial.isNotBlank()) {
                    "$partial\n\n⏹️ *Generation stopped.*"
                } else {
                    "⏹️ *Generation stopped.*"
                }
                sb.clear()
                sb.append(message)
            } catch (e: Throwable) {
                aiRepository.stopGeneration()
                val partial = sb.toString()
                val safeErr = SensitiveDataScrubber.scrub(e.message ?: e.javaClass.simpleName)
                val message = if (partial.isNotBlank()) {
                    "$partial\n\n❌ Error: $safeErr"
                } else {
                    "❌ Error: $safeErr"
                }
                sb.clear()
                sb.append(message)
            } finally {
                // Requirement C.2: Put finalize logic in finally block with withContext(NonCancellable) that always:
                // sets isGenerating=false, clears _streamingMessage, restores send button, saves response time,
                // attaches sources, and resumes background workers.
                withContext(NonCancellable) {
                    try {
                        val elapsedMs = System.currentTimeMillis() - queryStartTime
                        val rawContent = sb.toString().trim()
                        if (rawContent.isNotBlank()) {
                            val decision = responseObj?.routingDecision ?: _routingDecision.value
                            val sources = responseObj?.sourceDocs ?: emptyList()

                            val msgId = aiRepository.persistAssistantMessage(
                                sessionId = sessionId,
                                content = rawContent,
                                routingDecision = decision,
                                sourceDocs = sources
                            )
                            _responseTimes.update { it + (msgId to elapsedMs) }
                        }
                    } catch (t: Throwable) {
                        Log.e("ChatViewModel", "Error persisting message in finally: ${t.message}", t)
                    } finally {
                        _isLoading.value = false
                        _streamingMessage.value = null
                        _streamingSources.value = emptyList()
                        _isWaitingForModel.value = false
                        generationJob = null
                        InferenceCoordinator.setInferring(false)
                        InferenceCoordinator.setChatActive(false)
                        Log.d("DocSphereDebug", "finalize")
                    }
                }
            }
        }
    }

    fun newSession() {
        inMemoryChatManager.newChat()
        _streamingMessage.value = null
        nanoEngine.resetSession()
    }

    fun togglePersonalIntelligence() {
        viewModelScope.launch {
            val current = preferences.personalIntelligence.first()
            preferences.setPersonalIntelligence(!current)
        }
    }

    fun updateModelConfig(newConfig: ModelConfig) {
        val model = offlineModelManager.selectedModel.value
        val oldConfig = currentModelConfig.value
        viewModelScope.launch {
            nanoEngine.updateModelConfig(model, newConfig, oldConfig)
        }
    }

    fun resetModelConfigToDefaults() {
        val model = offlineModelManager.selectedModel.value
        val oldConfig = currentModelConfig.value
        viewModelScope.launch {
            nanoEngine.resetModelConfigToDefaults(model, oldConfig)
        }
    }

    fun openDocument(context: android.content.Context, documentId: String) {
        viewModelScope.launch {
            val doc = documentRepository.getDocumentById(documentId)
            if (doc != null) {
                com.docsphere.util.FileOpener.openWithSystem(context, doc)
            } else {
                android.widget.Toast.makeText(context, "Document not found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearHistory() {
        inMemoryChatManager.clearChat()
        _streamingMessage.value = null
        nanoEngine.resetSession()
    }

    fun selectModel(model: LlmModel) {
        viewModelScope.launch {
            offlineModelManager.selectModel(model)
            nanoEngine.warmup(forceReload = true)
        }
    }

    fun downloadModel(model: LlmModel) {
        offlineModelManager.downloadModel(model, viewModelScope)
    }

    fun deleteModel(model: LlmModel) {
        offlineModelManager.deleteModel(model, viewModelScope)
    }

    /**
     * Looks up the unmasked field value on-demand from document_fields.
     * The full value is NEVER kept in ChatUiState or in ViewModel properties.
     */
    suspend fun getFieldValue(documentId: String, fieldName: String): String? {
        return aiRepository.getFieldValue(documentId, fieldName)
    }
}
