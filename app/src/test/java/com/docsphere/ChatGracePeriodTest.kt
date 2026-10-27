package com.docsphere

import com.docsphere.data.repository.InMemoryChatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGracePeriodTest {

    @Test
    fun testChatPreservedWithinDefaultTwoMinuteGracePeriod() = runBlocking {
        val gracePeriodFlow = MutableStateFlow("2 minutes")
        var resetCalled = false
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = gracePeriodFlow.asStateFlow(),
            onResetCallback = { resetCalled = true }
        )

        val initialSessionId = chatManager.sessionId.value
        chatManager.addUserMessage("Show me my electricity bill")
        chatManager.addAssistantMessage("Your electricity bill is 450.")
        assertEquals(2, chatManager.messages.value.size)

        // App goes to background at t = 100000 ms
        val bgTime = 100000L
        chatManager.onAppBackgrounded(currentTime = bgTime)

        // User returns after 60 seconds (1 minute < 2 minutes grace period)
        val fgTime = bgTime + 60_000L
        chatManager.onAppForegrounded(currentTime = fgTime)

        // Give background scope a tiny moment to process
        kotlinx.coroutines.delay(50)

        // Chat should still be preserved
        assertEquals("Chat must be preserved when returning within grace period", 2, chatManager.messages.value.size)
        assertEquals(initialSessionId, chatManager.sessionId.value)
        assertTrue("Model reset must not be triggered", !resetCalled)
    }

    @Test
    fun testChatClearedAfterDefaultTwoMinuteGracePeriodExpires() = runBlocking {
        val gracePeriodFlow = MutableStateFlow("2 minutes")
        var resetCalled = false
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = gracePeriodFlow.asStateFlow(),
            onResetCallback = { resetCalled = true }
        )

        val initialSessionId = chatManager.sessionId.value
        chatManager.addUserMessage("Show me my bank statement")
        chatManager.addAssistantMessage("Your bank statement details are here.")
        assertEquals(2, chatManager.messages.value.size)

        // App goes to background at t = 100000 ms
        val bgTime = 100000L
        chatManager.onAppBackgrounded(currentTime = bgTime)

        // User returns after 130 seconds (> 120s grace period)
        val fgTime = bgTime + 130_000L
        chatManager.onAppForegrounded(currentTime = fgTime)

        // Give background scope time to execute clearChat
        kotlinx.coroutines.delay(50)

        // Chat must be completely cleared and session reset
        assertTrue("Chat messages must be cleared after grace period expires", chatManager.messages.value.isEmpty())
        assertNotEquals("Session ID must be refreshed", initialSessionId, chatManager.sessionId.value)
        assertTrue("Model conversation reset must be called", resetCalled)
    }

    @Test
    fun testChatClearedImmediatelyWhenSettingIsImmediately() = runBlocking {
        val gracePeriodFlow = MutableStateFlow("Immediately")
        var resetCalled = false
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = gracePeriodFlow.asStateFlow(),
            onResetCallback = { resetCalled = true }
        )

        chatManager.addUserMessage("What is my secret pin?")
        assertEquals(1, chatManager.messages.value.size)

        // App goes to background
        chatManager.onAppBackgrounded(currentTime = 50000L)
        kotlinx.coroutines.delay(50)

        // With "Immediately", chat should be cleared right on backgrounding
        assertTrue("Chat messages must be cleared immediately", chatManager.messages.value.isEmpty())
        assertTrue("Model conversation reset must be called", resetCalled)
    }

    @Test
    fun testChatPreservedAndClearedWithTenMinuteGracePeriod() = runBlocking {
        val gracePeriodFlow = MutableStateFlow("10 minutes")
        var resetCalled = false
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = gracePeriodFlow.asStateFlow(),
            onResetCallback = { resetCalled = true }
        )

        chatManager.addUserMessage("Summarize my tax return")
        assertEquals(1, chatManager.messages.value.size)

        val bgTime = 100000L
        chatManager.onAppBackgrounded(currentTime = bgTime)

        // Return after 5 minutes (300 seconds < 600 seconds) -> Preserved
        chatManager.onAppForegrounded(currentTime = bgTime + 300_000L)
        kotlinx.coroutines.delay(50)
        assertEquals(1, chatManager.messages.value.size)
        assertTrue(!resetCalled)

        // Leave again, and return after 11 minutes (660 seconds > 600 seconds) -> Cleared
        chatManager.onAppBackgrounded(currentTime = bgTime)
        chatManager.onAppForegrounded(currentTime = bgTime + 660_000L)
        kotlinx.coroutines.delay(50)
        assertTrue(chatManager.messages.value.isEmpty())
        assertTrue(resetCalled)
    }

    @Test
    fun testNewChatButtonResetsChatImmediately() {
        var resetCalled = false
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = MutableStateFlow("2 minutes"),
            onResetCallback = { resetCalled = true }
        )

        val initialSessionId = chatManager.sessionId.value
        chatManager.addUserMessage("Hello DocSphere")
        chatManager.addAssistantMessage("Hello! How can I help you?")
        assertEquals(2, chatManager.messages.value.size)

        chatManager.newChat()

        assertTrue("New chat must immediately clear all messages", chatManager.messages.value.isEmpty())
        assertNotEquals("New chat must create a fresh sessionId", initialSessionId, chatManager.sessionId.value)
        assertTrue("New chat must reset model conversation session", resetCalled)
    }
}
