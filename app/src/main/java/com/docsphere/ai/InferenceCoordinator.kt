package com.docsphere.ai

import android.os.Process
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates CPU/database activity between background workers (OCR, chunking, field extraction)
 * and active on-device LLM inference / interactive chat.
 *
 * When a user is chatting or LLM inference is running, workers pause and yield SQLite write locks
 * so that user questions do not freeze or hit database busy/lock timeouts or ANRs.
 *
 * Workers run at low priority (THREAD_PRIORITY_BACKGROUND) and are limited to one at a time via [workerMutex].
 */
object InferenceCoordinator {
    private val _isInferring = AtomicBoolean(false)
    private val _isChatActive = AtomicBoolean(false)
    private val _isModelLoading = AtomicBoolean(false)
    private val _isPersonalIntelligencePaused = AtomicBoolean(false)

    val workerMutex = Mutex()

    val isInferring: Boolean
        get() = _isInferring.get()

    val isChatActive: Boolean
        get() = _isChatActive.get()

    val isModelLoading: Boolean
        get() = _isModelLoading.get()

    val isPersonalIntelligencePaused: Boolean
        get() = _isPersonalIntelligencePaused.get()

    fun setInferring(inferring: Boolean) {
        _isInferring.set(inferring)
    }

    fun setChatActive(active: Boolean) {
        _isChatActive.set(active)
    }

    fun setModelLoading(loading: Boolean) {
        _isModelLoading.set(loading)
    }

    fun setPersonalIntelligencePaused(paused: Boolean) {
        _isPersonalIntelligencePaused.set(paused)
    }

    /**
     * Sets current thread priority to background to prevent starving the UI or query dispatchers.
     */
    fun setWorkerLowPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {}
    }

    /**
     * Called by background workers before heavy DB batch operations.
     * If the user is currently chatting, running an AI response, loading a model,
     * or Personal Intelligence is turned off, yields execution.
     */
    suspend fun pauseIfChatActive() {
        while (_isChatActive.get() || _isInferring.get() || _isModelLoading.get() || _isPersonalIntelligencePaused.get()) {
            delay(200)
            yield()
        }
    }

    suspend fun pauseIfInferring() {
        pauseIfChatActive()
    }

    /**
     * Executes work ensuring only one worker runs at a time, at low background priority.
     */
    suspend inline fun <T> withWorkerLock(crossinline action: suspend () -> T): T {
        return workerMutex.withLock {
            setWorkerLowPriority()
            pauseIfChatActive()
            action()
        }
    }
}

