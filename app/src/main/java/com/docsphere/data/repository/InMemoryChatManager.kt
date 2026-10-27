package com.docsphere.data.repository

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.docsphere.ai.InferenceCoordinator
import com.docsphere.ai.offline.GeminiNanoEngine
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RetrievedChunk
import com.docsphere.data.model.RoutingDecision
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.util.SensitiveDataScrubber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped in-memory manager for the active conversation.
 *
 * Requirements:
 * 1. Chat messages and sessions are NEVER saved to Room or any file on disk.
 * 2. When the app goes to the background (ProcessLifecycleOwner ON_STOP), a grace timer starts
 *    (Immediately / 2 minutes / 10 minutes, default 2 minutes).
 * 3. If the user returns within the grace period, the current chat is preserved.
 * 4. If the grace period expires, or the process is swiped/killed, a fresh empty chat starts on open.
 * 5. "New chat" immediately clears messages, resets the model session, and shows the welcome screen.
 */
@Singleton
class InMemoryChatManager(
    private val preferences: DocSpherePreferences?,
    private val nanoEngine: GeminiNanoEngine?,
    private val customGracePeriodFlow: kotlinx.coroutines.flow.Flow<String>? = null,
    private val customResetCallback: (() -> Unit)? = null
) : DefaultLifecycleObserver {

    @Inject
    constructor(
        preferences: DocSpherePreferences,
        nanoEngine: GeminiNanoEngine
    ) : this(preferences, nanoEngine, null, null)

    // Secondary constructor for testing without Android context or native engine
    constructor(
        gracePeriodFlow: kotlinx.coroutines.flow.Flow<String>,
        onResetCallback: (() -> Unit)? = null
    ) : this(null, null, gracePeriodFlow, onResetCallback)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessionId = MutableStateFlow(UUID.randomUUID().toString())
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var graceJob: Job? = null
    var backgroundTimestamp: Long = 0L
        internal set

    init {
        try {
            val mainLooper = android.os.Looper.getMainLooper()
            if (mainLooper != null) {
                val mainHandler = android.os.Handler(mainLooper)
                mainHandler.post {
                    try {
                        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                    } catch (_: Throwable) {
                        // Ignored in unit test environments
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignored in unit test environments
        }
    }

    private fun now(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppBackgrounded()
    }

    override fun onStart(owner: LifecycleOwner) {
        onAppForegrounded()
    }

    fun onAppBackgrounded(currentTime: Long = now()) {
        backgroundTimestamp = currentTime
        graceJob?.cancel()

        appScope.launch {
            val gracePeriodMs = getGracePeriodMs()
            if (gracePeriodMs <= 0L) {
                clearChat()
            } else {
                graceJob = appScope.launch {
                    delay(gracePeriodMs)
                    clearChat()
                }
            }
        }
    }

    fun onAppForegrounded(currentTime: Long = now()) {
        graceJob?.cancel()
        graceJob = null

        if (backgroundTimestamp > 0L) {
            val elapsed = currentTime - backgroundTimestamp
            appScope.launch {
                val gracePeriodMs = getGracePeriodMs()
                if (elapsed >= gracePeriodMs) {
                    clearChat()
                }
                backgroundTimestamp = 0L
            }
        }
    }

    suspend fun getGracePeriodMs(): Long {
        val flow = customGracePeriodFlow ?: preferences?.chatGracePeriod
        val setting = flow?.first() ?: "2 minutes"
        return when (setting) {
            "Immediately" -> 0L
            "10 minutes" -> 10 * 60 * 1000L
            else -> 2 * 60 * 1000L // Default 2 minutes
        }
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun addUserMessage(content: String): String {
        val id = UUID.randomUUID().toString()
        val msg = ChatMessage(
            id = id,
            sessionId = _sessionId.value,
            role = MessageRole.USER,
            content = content,
            timestamp = Instant.now()
        )
        addMessage(msg)
        return id
    }

    fun addAssistantMessage(
        content: String,
        routingDecision: RoutingDecision? = null,
        citations: List<RetrievedChunk> = emptyList()
    ): String {
        val id = UUID.randomUUID().toString()
        val safeContent = SensitiveDataScrubber.scrub(content)
        val msg = ChatMessage(
            id = id,
            sessionId = _sessionId.value,
            role = MessageRole.ASSISTANT,
            content = safeContent,
            timestamp = Instant.now(),
            routingDecision = routingDecision,
            citations = citations
        )
        addMessage(msg)
        return id
    }

    fun getMessages(): List<ChatMessage> = _messages.value

    fun clearChat() {
        _messages.value = emptyList()
        _sessionId.value = UUID.randomUUID().toString()
        nanoEngine?.resetConversation()
        customResetCallback?.invoke()
        try {
            InferenceCoordinator.setChatActive(false)
            InferenceCoordinator.setInferring(false)
        } catch (_: Throwable) {
            // Ignored in unit test environments
        }
    }

    fun newChat() {
        clearChat()
    }
}
