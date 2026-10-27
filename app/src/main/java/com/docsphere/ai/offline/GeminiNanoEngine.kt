package com.docsphere.ai.offline

import android.content.Context
import android.util.Log
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.util.SensitiveDataScrubber
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knowledgeBase: OfflineKnowledgeBase,
    private val reasoningEngine: DocumentReasoningEngine,
    private val modelManager: OfflineModelManager,
    private val identityResolver: com.docsphere.data.repository.UserIdentityResolver,
    private val preferences: DocSpherePreferences
) {
    companion object {
        const val DOCSPHERE_SYSTEM_PROMPT = "You are DocSphere AI, a friendly assistant on the user's phone. Answer helpfully and briefly."
    }

    private val tag = "DocSphereDebug"
    private val engineMutex = Mutex()

    private var currentEngine: Engine? = null
    private var currentConversation: Conversation? = null
    private var loadedModelPath: String? = null
    private var loadedBackendName: String? = null
    private var loadedMaxTokens: Int = 0

    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading.asStateFlow()

    private val _modelLoadingMessage = MutableStateFlow<String?>(null)
    val modelLoadingMessage: StateFlow<String?> = _modelLoadingMessage.asStateFlow()

    private val _loadingElapsedSeconds = MutableStateFlow(0)
    val loadingElapsedSeconds: StateFlow<Int> = _loadingElapsedSeconds.asStateFlow()

    private val _currentLoadingBackend = MutableStateFlow<String?>("GPU")

    private var activeLoadingJob: kotlinx.coroutines.Job? = null

    private val _gpuFallbackNotice = MutableStateFlow<String?>(null)
    val gpuFallbackNotice: StateFlow<String?> = _gpuFallbackNotice.asStateFlow()

    fun dismissGpuNotice() {
        _gpuFallbackNotice.value = null
    }

    fun isOnDeviceAiAvailable(): Boolean = true

    /**
     * Cancels active model loading and cleans up any partially loaded engine.
     */
    fun cancelLoading() {
        try {
            activeLoadingJob?.cancel()
            activeLoadingJob = null
        } catch (_: Throwable) {}
        closeModel()
        _isModelLoading.value = false
        _modelLoadingMessage.value = null
        _loadingElapsedSeconds.value = 0
        com.docsphere.ai.InferenceCoordinator.setModelLoading(false)
        Log.d(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Model loading cancelled by user.")
    }

    /**
     * Cancels the active token generation without unloading the engine from memory.
     */
    fun cancelInference() {
        try {
            currentConversation?.cancelProcess()
            Log.d(tag, "[GeminiNanoEngine] Token generation cancelled by user.")
        } catch (t: Throwable) {
            Log.w(tag, "[GeminiNanoEngine] cancelProcess warning: ${t.message}")
        }
    }

    /**
     * Unloads the engine and conversation completely.
     * Called only on model change or unrecoverable fatal engine errors.
     */
    fun closeModel() {
        try {
            currentConversation?.cancelProcess()
            currentConversation?.close()
        } catch (_: Throwable) {}
        try {
            currentEngine?.close()
        } catch (_: Throwable) {}

        currentConversation = null
        currentEngine = null
        loadedModelPath = null
        loadedBackendName = null
        loadedMaxTokens = 0
        Log.d(tag, "[GeminiNanoEngine] LiteRT-LM engine closed and freed.")
    }

    /**
     * Updates model configuration. If accelerator or maxTokens changed, reloads the engine;
     * otherwise only resets the conversation session with the new sampler settings.
     */
    suspend fun updateModelConfig(model: LlmModel, newConfig: ModelConfig, oldConfig: ModelConfig) {
        preferences.saveModelConfig(model, newConfig)
        if (newConfig.accelerator != oldConfig.accelerator || newConfig.maxTokens != oldConfig.maxTokens) {
            warmup(forceReload = true, targetModel = model)
        } else {
            resetSession(model, newConfig)
        }
    }

    /**
     * Resets settings for this model back to Gallery defaults.
     */
    suspend fun resetModelConfigToDefaults(model: LlmModel, oldConfig: ModelConfig) {
        val defaults = ModelConfig.defaultFor(model)
        updateModelConfig(model, defaults, oldConfig)
    }

    /**
     * Initializes or reuses the LiteRT-LM Engine on a background thread.
     * Created once at application scope. Never recreated on tab navigation, rotation,
     * settings screens or errors.
     */
    suspend fun warmup(forceReload: Boolean = false, targetModel: LlmModel? = null) = withContext(Dispatchers.IO) {
        val model = targetModel ?: modelManager.selectedModel.value
        if (model.isBuiltIn) {
            closeModel()
            _isModelLoading.value = false
            _modelLoadingMessage.value = null
            return@withContext
        }

        val modelFile = modelManager.modelFile(model)
        if (!modelFile.exists()) {
            closeModel()
            _isModelLoading.value = false
            _modelLoadingMessage.value = null
            return@withContext
        }

        val modelConfig = try {
            preferences.getModelConfig(model).first()
        } catch (_: Throwable) {
            ModelConfig.defaultFor(model)
        }

        val verifyStart = System.currentTimeMillis()
        Log.d(tag, "[$verifyStart] Load phase: verify file (${modelFile.length()} bytes)")
        val expectedSize = model.sizeInBytes
        if (expectedSize > 0L) {
            val actualSize = modelFile.length()
            if (actualSize != expectedSize) {
                Log.w(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Model file verification failed for ${model.displayName}: found $actualSize bytes, expected $expectedSize bytes. Deleting corrupted file.")
                try {
                    modelFile.delete()
                } catch (e: Exception) {
                    Log.e(tag, "[DocSphereDebug] Failed to delete corrupted model file: ${e.message}")
                }
                _isModelLoading.value = false
                _modelLoadingMessage.value = "Model file incomplete. Please download ${model.displayName} again in Settings."
                throw IllegalStateException("Model file for ${model.displayName} was corrupted ($actualSize vs $expectedSize bytes). Deleted file. Please download again.")
            }
        }

        val modelPath = modelFile.absolutePath
        val useGpuRequested = modelConfig.accelerator.equals("GPU", ignoreCase = true) && !modelConfig.gpuFailed
        val targetBackend = if (useGpuRequested) "GPU" else "CPU"

        engineMutex.withLock {
            if (currentEngine != null && loadedModelPath == modelPath && loadedBackendName == targetBackend && loadedMaxTokens == modelConfig.maxTokens && !forceReload) {
                if (currentConversation == null) {
                    resetSession(model, modelConfig)
                }
                return@withLock
            }

            activeLoadingJob = coroutineContext[kotlinx.coroutines.Job]

            _isModelLoading.value = true
            _currentLoadingBackend.value = targetBackend
            _loadingElapsedSeconds.value = 0
            _modelLoadingMessage.value = "Loading ${model.displayName} on $targetBackend... 0 s"
            closeModel()

            com.docsphere.ai.InferenceCoordinator.setModelLoading(true)

            var tickerJob: kotlinx.coroutines.Job? = null
            tickerJob = launch {
                var seconds = 0
                while (isActive) {
                    _loadingElapsedSeconds.value = seconds
                    val curBackend = _currentLoadingBackend.value ?: targetBackend
                    _modelLoadingMessage.value = "Loading ${model.displayName} on $curBackend... ${seconds} s"
                    delay(1000L)
                    seconds++
                }
            }

            try {
                val loadStartTime = System.currentTimeMillis()
                Log.d(tag, "[DocSphereDebug] [$loadStartTime] Load start: ${model.displayName}")

                val persistentCacheDir = File(context.filesDir, "litert_cache").apply { mkdirs() }.absolutePath
                val configuredMaxTokens = modelConfig.maxTokens

                var usedBackend = targetBackend
                var engine: Engine? = null

                // GPU loading with persistent OpenCL/Vulkan kernel cache and text-only config
                if (useGpuRequested) {
                    Log.d(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Backend: GPU | CacheDir: $persistentCacheDir | MaxTokens: $configuredMaxTokens")
                    try {
                        val engineStart = System.currentTimeMillis()
                        engine = kotlinx.coroutines.withTimeout(60_000L) {
                            val gpuConfig = EngineConfig(
                                modelPath = modelPath,
                                backend = Backend.GPU(),
                                visionBackend = null,
                                audioBackend = null,
                                maxNumTokens = configuredMaxTokens,
                                cacheDir = persistentCacheDir
                            )
                            val eng = Engine(gpuConfig)
                            val compileStart = System.currentTimeMillis()
                            eng.initialize()
                            val compileDuration = System.currentTimeMillis() - compileStart
                            Log.d(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Load phase: GPU compile/cache in ${compileDuration}ms")
                            eng
                        }
                        val engineCreatedTime = System.currentTimeMillis()
                        Log.d(tag, "[DocSphereDebug] [$engineCreatedTime] Load phase: GPU engine created in ${engineCreatedTime - engineStart}ms")
                    } catch (t: Throwable) {
                        val isTimeout = t is kotlinx.coroutines.TimeoutCancellationException || t.message?.contains("timed out", ignoreCase = true) == true
                        val reason = if (isTimeout) "GPU load timed out after 60s" else (t.message ?: "GPU initialization failed")
                        Log.w(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] GPU failed ($reason), falling back to CPU")
                        try { engine?.close() } catch (_: Throwable) {}
                        engine = null

                        preferences.markModelGpuFailed(model)
                        _gpuFallbackNotice.value = "GPU initialization failed for ${model.displayName}. Fallen back to CPU."
                        usedBackend = "CPU"
                        _currentLoadingBackend.value = "CPU"
                    }
                }

                // If GPU not requested or failed, load on CPU with 120s timeout
                if (engine == null) {
                    usedBackend = "CPU"
                    _currentLoadingBackend.value = "CPU"
                    Log.d(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Backend: CPU | CacheDir: $persistentCacheDir | MaxTokens: $configuredMaxTokens")
                    try {
                        val engineStart = System.currentTimeMillis()
                        engine = kotlinx.coroutines.withTimeout(120_000L) {
                            val cpuConfig = EngineConfig(
                                modelPath = modelPath,
                                backend = Backend.CPU(),
                                visionBackend = null,
                                audioBackend = null,
                                maxNumTokens = configuredMaxTokens,
                                cacheDir = persistentCacheDir
                            )
                            val eng = Engine(cpuConfig)
                            val compileStart = System.currentTimeMillis()
                            eng.initialize()
                            val compileDuration = System.currentTimeMillis() - compileStart
                            Log.d(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] Load phase: CPU compile/cache in ${compileDuration}ms")
                            eng
                        }
                        val engineCreatedTime = System.currentTimeMillis()
                        Log.d(tag, "[DocSphereDebug] [$engineCreatedTime] Load phase: CPU engine created in ${engineCreatedTime - engineStart}ms")
                    } catch (t: Throwable) {
                        try { engine?.close() } catch (_: Throwable) {}
                        engine = null
                        val isTimeout = t is kotlinx.coroutines.TimeoutCancellationException || t.message?.contains("timed out", ignoreCase = true) == true
                        val errorMsg = if (isTimeout) "Model loading timed out on CPU after 120 seconds." else "Model loading failed on CPU: ${t.message}"
                        Log.e(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] $errorMsg", t)
                        throw IllegalStateException(errorMsg, t)
                    }
                }

                currentEngine = engine
                loadedModelPath = modelPath
                loadedBackendName = usedBackend
                loadedMaxTokens = configuredMaxTokens

                resetSession(model, modelConfig)

                val loadEndTime = System.currentTimeMillis()
                Log.d(tag, "[DocSphereDebug] [$loadEndTime] Load complete in ${loadEndTime - loadStartTime}ms | Backend: $usedBackend")
            } catch (t: Throwable) {
                closeModel()
                val errorMsg = SensitiveDataScrubber.scrub(t.message ?: t.javaClass.simpleName)
                Log.e(tag, "[DocSphereDebug] [${System.currentTimeMillis()}] LiteRT-LM initialization failed: $errorMsg", t)
                _isModelLoading.value = false
                _modelLoadingMessage.value = null
                throw IllegalStateException("Model failed to load: $errorMsg", t)
            } finally {
                tickerJob?.cancel()
                com.docsphere.ai.InferenceCoordinator.setModelLoading(false)
                _isModelLoading.value = false
                _modelLoadingMessage.value = null
                _loadingElapsedSeconds.value = 0
                activeLoadingJob = null
            }
        }
    }

    /**
     * Resets the active conversation session.
     * Reset only on "New chat", model change, or config change.
     */
    fun resetSession(model: LlmModel? = null, config: ModelConfig? = null, systemPrompt: String = DOCSPHERE_SYSTEM_PROMPT) {
        val engine = currentEngine ?: return
        try {
            currentConversation?.cancelProcess()
            currentConversation?.close()
        } catch (_: Throwable) {}

        val activeModel = model ?: modelManager.selectedModel.value
        val activeConfig = config ?: ModelConfig.defaultFor(activeModel)
        val samplerConfig = SamplerConfig(
            topK = activeConfig.topK,
            topP = activeConfig.topP.toDouble(),
            temperature = activeConfig.temperature.toDouble()
        )
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = if (systemPrompt.isNotBlank()) Contents.of(Content.Text(systemPrompt)) else null
        )
        currentConversation = engine.createConversation(conversationConfig)
        Log.d(tag, "[GeminiNanoEngine] Conversation session reset for ${activeModel.displayName}")
    }

    fun resetConversation() {
        resetSession()
    }

    /**
     * Resets the session and silently re-seeds the last 2 turns when nearing the token limit (~80%).
     */
    private fun resetSessionWithLastTurns(
        engine: Engine,
        config: ModelConfig,
        historyTurns: List<Pair<String, String>>,
        systemPrompt: String = DOCSPHERE_SYSTEM_PROMPT
    ) {
        try {
            currentConversation?.cancelProcess()
            currentConversation?.close()
        } catch (_: Throwable) {}

        val lastTurns = historyTurns.takeLast(4)
        val initialMessages = mutableListOf<Message>()
        for ((role, text) in lastTurns) {
            if (text.isNotBlank()) {
                val msg = if (role.equals("User", ignoreCase = true)) {
                    Message.user(text)
                } else {
                    Message.model(text)
                }
                initialMessages.add(msg)
            }
        }

        val samplerConfig = SamplerConfig(
            topK = config.topK,
            topP = config.topP.toDouble(),
            temperature = config.temperature.toDouble()
        )
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = if (systemPrompt.isNotBlank()) Contents.of(Content.Text(systemPrompt)) else null,
            initialMessages = initialMessages.toList()
        )
        currentConversation = engine.createConversation(conversationConfig)
        Log.d(tag, "[GeminiNanoEngine] Re-seeded session with ${initialMessages.size} messages")
    }

    /**
     * Runs offline inference.
     * When personalIntelligence is false: runs pure Gallery style with reused multi-turn conversation.
     * When personalIntelligence is true: runs existing retrieval pipeline with fresh conversation per question.
     */
    fun infer(
        systemPrompt: String = DOCSPHERE_SYSTEM_PROMPT,
        userQuery: String,
        contextChunks: List<String> = emptyList(),
        historyTurns: List<Pair<String, String>> = emptyList(),
        targetModel: LlmModel? = null,
        personalIntelligence: Boolean = true
    ): Flow<String> = flow {
        Log.d("DocSphereDebug", "generation start")
        val trimmed = userQuery.trim()
        val activeModel = targetModel ?: modelManager.selectedModel.value

        if (activeModel != LlmModel.BUILT_IN) {
            if (!modelManager.isModelInstalled(activeModel)) {
                emit("❌ **Model file not installed.**\n\nPlease download **${activeModel.displayName}** in Settings > AI Models first.")
                return@flow
            }

            if (_isModelLoading.value) {
                try {
                    _isModelLoading.first { !it }
                } catch (_: Throwable) {}
            }

            var retryCount = 0
            val maxRetries = 1
            var success = false

            while (!success && retryCount <= maxRetries) {
                try {
                    warmup(targetModel = activeModel)

                    val config = try {
                        preferences.getModelConfig(activeModel).first()
                    } catch (_: Throwable) {
                        ModelConfig.defaultFor(activeModel)
                    }

                    var emittedAny = false
                    val neuralFlow = if (!personalIntelligence) {
                        inferWithNeuralModelGallery(trimmed, historyTurns, activeModel, config)
                    } else {
                        inferWithNeuralModelRetrieval(systemPrompt, trimmed, contextChunks, historyTurns, activeModel, config)
                    }

                    neuralFlow.collect { token ->
                        emittedAny = true
                        emit(token)
                    }

                    if (!emittedAny) {
                        throw IllegalStateException("The offline model did not generate output.")
                    }
                    success = true
                    return@flow
                } catch (t: Throwable) {
                    val rawReason = t.cause?.message ?: t.message ?: t.javaClass.simpleName
                    val cleanReason = if (rawReason.startsWith("Model failed to load:", ignoreCase = true)) {
                        rawReason.removePrefix("Model failed to load:").trim()
                    } else {
                        rawReason
                    }
                    val isSessionError = cleanReason.contains("Status Code: 9", ignoreCase = true) ||
                            cleanReason.contains("Status Code: 13", ignoreCase = true) ||
                            cleanReason.contains("capacity", ignoreCase = true) ||
                            cleanReason.contains("failed with state", ignoreCase = true) ||
                            cleanReason.contains("state: 7", ignoreCase = true) ||
                            cleanReason.contains("Task failed", ignoreCase = true)

                    if (retryCount < maxRetries && isSessionError) {
                        retryCount++
                        Log.w(tag, "[DocSphereDebug] Session error ($cleanReason). Recreating session and retrying (attempt $retryCount)...")
                        resetSession(activeModel)
                        if (cleanReason.contains("Status Code: 13") || cleanReason.contains("state: 7")) {
                            warmup(forceReload = true)
                        }
                    } else {
                        val reason = SensitiveDataScrubber.scrub(cleanReason)
                        Log.e(tag, "[DocSphereDebug] LiteRT-LM inference failed after retry: $reason", t)
                        emit("❌ **Model error: $reason**\n\n*Target Model:* ${activeModel.displayName}")
                        return@flow
                    }
                }
            }
            return@flow
        }

        // BUILT_IN Instant Engine Fallback
        val isExpanded = modelManager.isExpandedModelActive()
        val resolvedName = identityResolver.resolveUserName()
        val smartResponse = generateSmartOfflineResponse(trimmed, contextChunks, isExpanded, resolvedName)
        val answer = smartResponse ?: synthesizeModelResponse(trimmed, contextChunks, activeModel, resolvedName)
        val labeledAnswer = if (contextChunks.isNotEmpty() && !answer.startsWith("📄")) {
            "📄 **Found in your documents**\n\n$answer"
        } else {
            answer
        }
        val words = labeledAnswer.split(" ")
        val effectiveWords = if (contextChunks.isNotEmpty()) words.subList(0, minOf(words.size, 300)) else words
        val chunkSize = 3
        var first = true
        for (i in effectiveWords.indices step chunkSize) {
            if (first) {
                first = false
                Log.d("DocSphereDebug", "first token")
            }
            val chunk = effectiveWords.subList(i, minOf(i + chunkSize, effectiveWords.size)).joinToString(" ") + " "
            emit(chunk)
            delay(12)
        }
        Log.d("DocSphereDebug", "last token")
        Log.d("DocSphereDebug", "done callback")
    }.flowOn(Dispatchers.Default)

    /**
     * Pure Gallery-Style Multi-Turn Chat (Personal Intelligence OFF).
     * Reuses one conversation session. Memory holds history natively without prompt re-seeding.
     * If token usage reaches ~80%, resets session and silently re-seeds the last 2 turns.
     * DeepSeek-R1 <think> block is hidden.
     */
    private fun inferWithNeuralModelGallery(
        userQuery: String,
        historyTurns: List<Pair<String, String>>,
        activeModel: LlmModel,
        config: ModelConfig
    ): Flow<String> = callbackFlow {
        val engine = currentEngine
        if (engine == null) {
            trySend("❌ **Model not available.** Please ensure **${activeModel.displayName}** is downloaded in Settings.")
            close()
            return@callbackFlow
        }

        val currentTokens = try {
            currentConversation?.getTokenCount() ?: 0
        } catch (_: Throwable) { 0 }

        val maxTokens = config.maxTokens
        val threshold = (maxTokens * 0.8).toInt()

        if (currentConversation == null || (currentTokens > 0 && currentTokens >= threshold)) {
            Log.d(tag, "[GeminiNanoEngine] Token count ($currentTokens) reached ~80% of limit ($threshold). Resetting and re-seeding last 2 turns.")
            resetSessionWithLastTurns(engine, config, historyTurns)
        }

        val conversation = currentConversation ?: run {
            resetSession(activeModel, config)
            currentConversation!!
        }

        val inferStartTime = System.currentTimeMillis()
        val hasReceivedFirstToken = AtomicBoolean(false)
        val isDone = AtomicBoolean(false)
        val isCancelled = AtomicBoolean(false)
        var tokenCount = 0
        var lastTokenTime = System.currentTimeMillis()
        var timeToFirstTokenMs = -1L

        val thinkFilter = if (activeModel == LlmModel.DEEPSEEK_R1_1_5B || activeModel.isReasoning) ThinkBlockFilter() else null

        val watchdog = launch {
            val firstTokenDeadline = System.currentTimeMillis() + 90_000L
            while (!isDone.get()) {
                delay(500)
                val now = System.currentTimeMillis()
                val inactiveMs = now - lastTokenTime

                if (hasReceivedFirstToken.get() && inactiveMs > 10_000L && !isDone.get()) {
                    Log.w("DocSphereDebug", "Safety net: 10s inactivity detected with no engine activity. Finalizing generation.")
                    try { conversation.cancelProcess() } catch (_: Throwable) {}
                    isDone.set(true)
                    Log.d("DocSphereDebug", "last token")
                    Log.d("DocSphereDebug", "done callback")
                    close()
                    return@launch
                } else if (!hasReceivedFirstToken.get() && now > firstTokenDeadline) {
                    Log.w(tag, "[DocSphereDebug] First token timeout (90s) exceeded")
                    try { conversation.cancelProcess() } catch (_: Throwable) {}
                    trySend("⏱️ *First-token timeout (90s exceeded). Please try asking a more specific question.*")
                    isDone.set(true)
                    close()
                    return@launch
                }
            }
        }

        conversation.sendMessageAsync(
            Contents.of(Content.Text(userQuery)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val now = System.currentTimeMillis()
                    if (!hasReceivedFirstToken.getAndSet(true)) {
                        timeToFirstTokenMs = now - inferStartTime
                        Log.d("DocSphereDebug", "first token")
                        Log.d(tag, "[DocSphereDebug] [$now] Load phase: first token in ${timeToFirstTokenMs}ms")
                    }
                    lastTokenTime = now
                    tokenCount++
                    val delta = message.toString()
                    if (delta.isNotEmpty()) {
                        val filtered = thinkFilter?.process(delta) ?: delta
                        if (filtered.isNotEmpty()) {
                            trySend(filtered)
                        }
                    }
                }

                override fun onDone() {
                    isDone.set(true)
                    val inferDurationMs = System.currentTimeMillis() - inferStartTime
                    val durationSec = inferDurationMs / 1000.0
                    val speed = if (durationSec > 0) String.format(Locale.US, "%.1f", tokenCount / durationSec) else "0.0"
                    Log.d("DocSphereDebug", "last token")
                    Log.d("DocSphereDebug", "done callback")
                    Log.d(
                        tag,
                        "[DocSphereDebug] Inference Completed | Backend: $loadedBackendName | Model: ${activeModel.fileName} | Total Tokens: $tokenCount | Duration: ${inferDurationMs}ms | First Token: ${timeToFirstTokenMs}ms | Speed: $speed tokens/sec"
                    )
                    Log.i(
                        tag,
                        "[DocSphereDebug] Inference Metric: Model=${activeModel.displayName}, TTFT=${timeToFirstTokenMs}ms, Speed=$speed tok/s, Total Tokens=$tokenCount, Duration=${inferDurationMs}ms"
                    )
                    close()
                }

                override fun onError(throwable: Throwable) {
                    isDone.set(true)
                    if (!isCancelled.get()) {
                        val errMsg = SensitiveDataScrubber.scrub(throwable.message ?: throwable.javaClass.simpleName)
                        Log.e(tag, "[DocSphereDebug] Inference error: $errMsg", throwable)
                        close(throwable)
                    } else {
                        close()
                    }
                }
            }
        )

        awaitClose {
            isCancelled.set(true)
            watchdog.cancel()
            try {
                conversation.cancelProcess()
            } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Retrieval Pipeline Path (Personal Intelligence ON).
     * Fresh conversation per question with context excerpts + last 2 clean turns to prevent overflow.
     */
    private fun inferWithNeuralModelRetrieval(
        systemPrompt: String,
        userQuery: String,
        contextChunks: List<String>,
        historyTurns: List<Pair<String, String>>,
        activeModel: LlmModel,
        config: ModelConfig
    ): Flow<String> = callbackFlow {
        val engine = currentEngine
        if (engine == null) {
            trySend("❌ **Model not available.** Please ensure **${activeModel.displayName}** is downloaded in Settings.")
            close()
            return@callbackFlow
        }

        val samplerConfig = SamplerConfig(
            topK = config.topK,
            topP = config.topP.toDouble(),
            temperature = config.temperature.toDouble()
        )
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = if (systemPrompt.isNotBlank()) Contents.of(Content.Text(systemPrompt)) else null
        )
        val conversation = engine.createConversation(conversationConfig)

        val configuredMaxTokens = config.maxTokens
        val maxTokens = if (contextChunks.isNotEmpty()) 250 else 400
        val reservedMargin = 100
        val availableInputTokens = (configuredMaxTokens - maxTokens - reservedMargin).coerceAtLeast(300)

        fun estimateTokens(text: String): Int = (text.length / 3.5).toInt() + 2

        fun buildPrompt(chunks: List<String>, history: List<Pair<String, String>>, query: String): String {
            val sb = StringBuilder()
            val contextPiece = chunks.firstOrNull { it.isNotBlank() }
            if (contextPiece != null) {
                sb.append(contextPiece.trim()).append("\n\n")
            }
            if (history.isNotEmpty()) {
                sb.append("Recent conversation:\n")
                for ((role, text) in history) {
                    sb.append("$role: $text\n")
                }
                sb.append("\n")
            }
            if (contextPiece != null) {
                sb.append("DocSphere AI:")
            } else {
                sb.append("User: $query\nDocSphere AI:")
            }
            return sb.toString()
        }

        val activeHistory = historyTurns.takeLast(4).toMutableList()
        val activeChunks = contextChunks.take(3).toMutableList()

        var fullPrompt = buildPrompt(activeChunks, activeHistory, userQuery)
        while (estimateTokens(fullPrompt) > availableInputTokens && activeHistory.isNotEmpty()) {
            activeHistory.removeAt(0)
            fullPrompt = buildPrompt(activeChunks, activeHistory, userQuery)
        }
        while (estimateTokens(fullPrompt) > availableInputTokens && activeChunks.isNotEmpty()) {
            activeChunks.removeAt(activeChunks.size - 1)
            fullPrompt = buildPrompt(activeChunks, activeHistory, userQuery)
        }
        if (estimateTokens(fullPrompt) > availableInputTokens && activeChunks.isNotEmpty()) {
            val excessTokens = estimateTokens(fullPrompt) - availableInputTokens
            val excessChars = excessTokens * 4
            val trimmedChunk = activeChunks[0].dropLast(excessChars.coerceAtMost(activeChunks[0].length))
            activeChunks[0] = trimmedChunk
            fullPrompt = buildPrompt(activeChunks, activeHistory, userQuery)
        }

        val promptLength = fullPrompt.length
        val estimatedPromptTokens = estimateTokens(fullPrompt)
        val historyTurnsCount = activeHistory.size
        Log.d(
            tag,
            "[DocSphereDebug] Inference Starting | Backend: $loadedBackendName | Model: ${activeModel.fileName} | Prompt Length: $promptLength chars | Est Tokens: $estimatedPromptTokens / $availableInputTokens | History Turns Sent: $historyTurnsCount"
        )

        val inferStartTime = System.currentTimeMillis()
        val hasReceivedFirstToken = AtomicBoolean(false)
        val isDone = AtomicBoolean(false)
        val isCancelled = AtomicBoolean(false)
        var tokenCount = 0
        var lastTokenTime = System.currentTimeMillis()
        var timeToFirstTokenMs = -1L

        val thinkFilter = if (activeModel == LlmModel.DEEPSEEK_R1_1_5B || activeModel.isReasoning) ThinkBlockFilter() else null

        val watchdog = launch {
            val firstTokenDeadline = System.currentTimeMillis() + 90_000L
            while (!isDone.get()) {
                delay(500)
                val now = System.currentTimeMillis()
                val inactiveMs = now - lastTokenTime

                if (hasReceivedFirstToken.get() && inactiveMs > 10_000L && !isDone.get()) {
                    Log.w("DocSphereDebug", "Safety net: 10s inactivity detected with no engine activity. Finalizing generation.")
                    try { conversation.cancelProcess() } catch (_: Throwable) {}
                    isDone.set(true)
                    Log.d("DocSphereDebug", "last token")
                    Log.d("DocSphereDebug", "done callback")
                    close()
                    return@launch
                } else if (!hasReceivedFirstToken.get() && now > firstTokenDeadline) {
                    Log.w(tag, "[DocSphereDebug] First token timeout (90s) exceeded")
                    try { conversation.cancelProcess() } catch (_: Throwable) {}
                    trySend("⏱️ *First-token timeout (90s exceeded). Please try asking a more specific question.*")
                    isDone.set(true)
                    close()
                    return@launch
                }
            }
        }

        conversation.sendMessageAsync(
            Contents.of(Content.Text(fullPrompt)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val now = System.currentTimeMillis()
                    if (!hasReceivedFirstToken.getAndSet(true)) {
                        timeToFirstTokenMs = now - inferStartTime
                        Log.d("DocSphereDebug", "first token")
                        Log.d(tag, "[DocSphereDebug] [$now] Load phase: first token in ${timeToFirstTokenMs}ms")
                    }
                    lastTokenTime = now
                    tokenCount++
                    val delta = message.toString()
                    if (delta.isNotEmpty()) {
                        val filtered = thinkFilter?.process(delta) ?: delta
                        if (filtered.isNotEmpty()) {
                            trySend(filtered)
                        }
                    }
                    if (tokenCount >= maxTokens) {
                        isDone.set(true)
                        Log.d("DocSphereDebug", "last token")
                        try { conversation.cancelProcess() } catch (_: Throwable) {}
                        close()
                    }
                }

                override fun onDone() {
                    isDone.set(true)
                    val inferDurationMs = System.currentTimeMillis() - inferStartTime
                    val durationSec = inferDurationMs / 1000.0
                    val speed = if (durationSec > 0) String.format(Locale.US, "%.1f", tokenCount / durationSec) else "0.0"
                    Log.d("DocSphereDebug", "last token")
                    Log.d("DocSphereDebug", "done callback")
                    Log.d(
                        tag,
                        "[DocSphereDebug] Inference Completed | Backend: $loadedBackendName | Model: ${activeModel.fileName} | Total Tokens: $tokenCount | Duration: ${inferDurationMs}ms | First Token: ${timeToFirstTokenMs}ms | Speed: $speed tokens/sec"
                    )
                    Log.i(
                        tag,
                        "[DocSphereDebug] Inference Metric: Model=${activeModel.displayName}, Prompt Length=$promptLength chars, TTFT=${timeToFirstTokenMs}ms, Speed=$speed tok/s, Total Tokens=$tokenCount, Duration=${inferDurationMs}ms"
                    )
                    close()
                }

                override fun onError(throwable: Throwable) {
                    isDone.set(true)
                    if (!isCancelled.get()) {
                        val errMsg = throwable.message ?: throwable.javaClass.simpleName
                        Log.e(tag, "[DocSphereDebug] Inference error: $errMsg", throwable)
                        close(throwable)
                    } else {
                        close()
                    }
                }
            }
        )

        awaitClose {
            isCancelled.set(true)
            watchdog.cancel()
            try {
                conversation.cancelProcess()
            } catch (_: Throwable) {}
            try {
                conversation.close()
            } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    // Helper to hide DeepSeek-R1 <think>...</think> block during streaming
    private class ThinkBlockFilter {
        private var insideThink = false
        private val buffer = StringBuilder()

        fun process(chunk: String): String {
            buffer.append(chunk)
            val out = StringBuilder()

            while (buffer.isNotEmpty()) {
                if (!insideThink) {
                    val startIdx = buffer.indexOf("<think>")
                    if (startIdx != -1) {
                        out.append(buffer.substring(0, startIdx))
                        buffer.delete(0, startIdx + 7)
                        insideThink = true
                    } else {
                        val partialIdx = buffer.lastIndexOf('<')
                        if (partialIdx != -1 && "<think>".startsWith(buffer.substring(partialIdx))) {
                            out.append(buffer.substring(0, partialIdx))
                            val keep = buffer.substring(partialIdx)
                            buffer.clear()
                            buffer.append(keep)
                            break
                        } else {
                            out.append(buffer)
                            buffer.clear()
                        }
                    }
                } else {
                    val endIdx = buffer.indexOf("</think>")
                    if (endIdx != -1) {
                        buffer.delete(0, endIdx + 8)
                        insideThink = false
                        while (buffer.isNotEmpty() && (buffer[0] == '\n' || buffer[0] == '\r')) {
                            buffer.deleteCharAt(0)
                        }
                    } else {
                        val partialIdx = buffer.lastIndexOf('<')
                        if (partialIdx != -1 && "</think>".startsWith(buffer.substring(partialIdx))) {
                            val keep = buffer.substring(partialIdx)
                            buffer.clear()
                            buffer.append(keep)
                        } else {
                            buffer.clear()
                        }
                        break
                    }
                }
            }
            return out.toString()
        }
    }

    // Heuristic Fallback helpers (Preserved for BUILT_IN Instant mode)
    private fun generateSmartOfflineResponse(
        query: String,
        contextChunks: List<String>,
        isExpanded: Boolean,
        resolvedName: String?
    ): String? {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        if (lower in listOf("who are you", "what are you", "what is docsphere", "what can you do")) {
            return "I am **Docsphere AI**, your on-device document assistant. I can extract identity details, parse invoices, search your stored files, and answer questions about your documents."
        }

        if (contextChunks.isNotEmpty()) {
            val reasoning = reasoningEngine.reason(trimmed, contextChunks, isExpanded)
            if (!reasoning.isNullOrBlank()) {
                return reasoning
            }
        }

        return null
    }

    private fun synthesizeModelResponse(
        query: String,
        contextChunks: List<String>,
        model: LlmModel,
        resolvedName: String?
    ): String {
        if (contextChunks.isNotEmpty()) {
            val reasoning = reasoningEngine.reason(query, contextChunks, modelManager.isExpandedModelActive())
            if (!reasoning.isNullOrBlank()) {
                return reasoning
            }
            return "I couldn't find a direct answer to that in your documents."
        }

        return "I couldn't find that in your documents."
    }
}
