package com.docsphere

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit test verifying Chat generation lifecycle and safety nets (Requirement C.5):
 * 1. Normal finish: asserts isGenerating=false
 * 2. Error: asserts isGenerating=false
 * 3. Cancel: asserts isGenerating=false
 * 4. Missing final callback (safety net watchdog): asserts isGenerating=false
 */
class ChatGenerationLifecycleTest {

    interface FakeInferenceEngine {
        fun generate(query: String): Flow<String>
        fun cancel()
    }

    class TestChatController(private val engine: FakeInferenceEngine) {
        var isGenerating: Boolean = false
        var streamingMessage: String? = null
        var isSendButtonEnabled: Boolean = true
        var finalizedSourcesAttached: Boolean = false
        var responseTimeSaved: Boolean = false
        var backgroundWorkersResumed: Boolean = false
        var persistedMessage: String? = null

        fun sendMessage(scope: CoroutineScope, query: String, inactivityTimeoutMs: Long = 150L): Job {
            isGenerating = true
            isSendButtonEnabled = false
            streamingMessage = null
            finalizedSourcesAttached = false
            responseTimeSaved = false
            backgroundWorkersResumed = false

            return scope.launch {
                val sb = StringBuilder()
                var lastChangeTime = System.currentTimeMillis()

                try {
                    val watchdog = launch {
                        while (isActive) {
                            delay(20)
                            val now = System.currentTimeMillis()
                            if (sb.isNotEmpty() && (now - lastChangeTime > inactivityTimeoutMs)) {
                                engine.cancel()
                                break
                            }
                        }
                    }

                    try {
                        engine.generate(query).collect { token ->
                            sb.append(token)
                            lastChangeTime = System.currentTimeMillis()
                            streamingMessage = sb.toString()
                        }
                    } finally {
                        watchdog.cancel()
                    }
                } catch (_: CancellationException) {
                    val partial = sb.toString()
                    sb.clear()
                    sb.append(if (partial.isNotBlank()) "$partial\n\n⏹️ Stopped" else "⏹️ Stopped")
                } catch (_: Throwable) {
                    val partial = sb.toString()
                    sb.clear()
                    sb.append(if (partial.isNotBlank()) "$partial\n\n❌ Error" else "❌ Error")
                } finally {
                    withContext(NonCancellable) {
                        try {
                            persistedMessage = sb.toString().trim()
                            finalizedSourcesAttached = true
                            responseTimeSaved = true
                        } finally {
                            isGenerating = false
                            streamingMessage = null
                            isSendButtonEnabled = true
                            backgroundWorkersResumed = true
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testNormalFinish_assertsNotGenerating() = runBlocking {
        val fakeEngine = object : FakeInferenceEngine {
            override fun generate(query: String): Flow<String> = flow {
                emit("Hello")
                delay(10)
                emit(" world")
                delay(10)
                emit("!")
            }
            override fun cancel() {}
        }

        val controller = TestChatController(fakeEngine)
        val job = controller.sendMessage(this, "test")
        assertTrue("isGenerating must be true while generating", controller.isGenerating)
        assertFalse("Send button should be disabled", controller.isSendButtonEnabled)

        job.join()

        assertFalse("isGenerating must be false after normal finish", controller.isGenerating)
        assertTrue("Send button must be restored", controller.isSendButtonEnabled)
        assertNull("Streaming message must be cleared", controller.streamingMessage)
        assertTrue("Sources must be attached", controller.finalizedSourcesAttached)
        assertTrue("Response time must be saved", controller.responseTimeSaved)
        assertTrue("Background workers must be resumed", controller.backgroundWorkersResumed)
        assertEquals("Hello world!", controller.persistedMessage)
    }

    @Test
    fun testError_assertsNotGenerating() = runBlocking {
        val fakeEngine = object : FakeInferenceEngine {
            override fun generate(query: String): Flow<String> = flow {
                emit("Partial text")
                delay(10)
                throw RuntimeException("Simulated engine crash")
            }
            override fun cancel() {}
        }

        val controller = TestChatController(fakeEngine)
        val job = controller.sendMessage(this, "test")
        job.join()

        assertFalse("isGenerating must be false after error", controller.isGenerating)
        assertTrue("Send button must be restored", controller.isSendButtonEnabled)
        assertNull("Streaming message must be cleared", controller.streamingMessage)
        assertTrue("Background workers must be resumed", controller.backgroundWorkersResumed)
        assertTrue(controller.persistedMessage?.contains("❌ Error") == true)
    }

    @Test
    fun testCancel_assertsNotGenerating() = runBlocking {
        val cancelledCalled = AtomicBoolean(false)
        val fakeEngine = object : FakeInferenceEngine {
            override fun generate(query: String): Flow<String> = flow {
                emit("Token 1")
                delay(500)
                emit("Token 2")
            }
            override fun cancel() {
                cancelledCalled.set(true)
            }
        }

        val controller = TestChatController(fakeEngine)
        val job = controller.sendMessage(this, "test")
        delay(50)
        assertTrue("isGenerating must be true before cancellation", controller.isGenerating)

        job.cancel()
        job.join()

        assertFalse("isGenerating must be false after cancellation", controller.isGenerating)
        assertTrue("Send button must be restored", controller.isSendButtonEnabled)
        assertNull("Streaming message must be cleared", controller.streamingMessage)
        assertTrue("Background workers must be resumed", controller.backgroundWorkersResumed)
        assertTrue(controller.persistedMessage?.contains("⏹️ Stopped") == true)
    }

    @Test
    fun testMissingFinalCallback_watchdogFinalizesAndAssertsNotGenerating() = runBlocking {
        val cancelledCalled = AtomicBoolean(false)
        val fakeEngine = object : FakeInferenceEngine {
            val hangingFlowChannel = CompletableDeferred<Unit>()
            override fun generate(query: String): Flow<String> = flow {
                emit("First tokens emitted")
                // Missing final callback: simulates engine hanging without calling onDone
                hangingFlowChannel.await()
            }
            override fun cancel() {
                cancelledCalled.set(true)
                hangingFlowChannel.complete(Unit)
            }
        }

        val controller = TestChatController(fakeEngine)
        val job = controller.sendMessage(this, "test", inactivityTimeoutMs = 100L)
        delay(30)
        assertTrue("isGenerating must be true while waiting", controller.isGenerating)

        // Wait for inactivity watchdog to fire and complete
        job.join()

        assertTrue("Watchdog must have called cancel() on engine", cancelledCalled.get())
        assertFalse("isGenerating must be false after watchdog safety net triggers", controller.isGenerating)
        assertTrue("Send button must be restored", controller.isSendButtonEnabled)
        assertNull("Streaming message must be cleared", controller.streamingMessage)
        assertTrue("Background workers must be resumed", controller.backgroundWorkersResumed)
        assertEquals("First tokens emitted", controller.persistedMessage)
    }
}
