package com.docsphere

import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docsphere.data.local.DocSphereDatabase
import com.docsphere.data.repository.InMemoryChatManager
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class ChatNoDatabaseStorageTest {

    @Test
    fun testChatMessageEntityRemovedFromProject() {
        val classExists = try {
            Class.forName("com.docsphere.data.local.ChatMessageEntity")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertFalse("ChatMessageEntity class must be completely deleted from the project", classExists)
    }

    @Test
    fun testDatabaseHasNoChatMessageDaoMethod() {
        val methods = DocSphereDatabase::class.java.methods
        val daoMethods = methods.filter {
            it.name.contains("chatMessage", ignoreCase = true) ||
            it.returnType.name.contains("ChatMessageDao", ignoreCase = true)
        }
        assertTrue(
            "DocSphereDatabase must not have any chat message DAO method",
            daoMethods.isEmpty()
        )
    }

    @Test
    fun testMigrationDropsChatMessagesTable() {
        val executedSql = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        assertEquals(6, DocSphereDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, DocSphereDatabase.MIGRATION_6_7.endVersion)

        DocSphereDatabase.MIGRATION_6_7.migrate(fakeDb)

        assertTrue(
            "MIGRATION_6_7 must drop chat_messages table to purge all sensitive chat history",
            executedSql.any { it.replace("`", "").contains("DROP TABLE IF EXISTS chat_messages", ignoreCase = true) }
        )
    }

    @Test
    fun testCompoundMigrationsDropChatMessagesTable() {
        val compoundMigrations = listOf(
            DocSphereDatabase.MIGRATION_1_7,
            DocSphereDatabase.MIGRATION_2_7,
            DocSphereDatabase.MIGRATION_3_7,
            DocSphereDatabase.MIGRATION_4_7,
            DocSphereDatabase.MIGRATION_5_7
        )

        for (migration in compoundMigrations) {
            val executedSql = mutableListOf<String>()
            val fakeDb = Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                    executedSql.add(args[0].toString())
                }
                null
            } as SupportSQLiteDatabase

            assertEquals(7, migration.endVersion)
            migration.migrate(fakeDb)

            assertTrue(
                "Migration from ${migration.startVersion} to 7 must drop chat_messages table",
                executedSql.any { it.replace("`", "").contains("DROP TABLE IF EXISTS chat_messages", ignoreCase = true) }
            )
        }
    }

    @Test
    fun testChatOperationsAreInMemoryOnly() {
        val chatManager = InMemoryChatManager(
            gracePeriodFlow = flowOf("2 minutes")
        )

        assertTrue(chatManager.messages.value.isEmpty())

        val userMsgId = chatManager.addUserMessage("What is my passport number?")
        assertEquals(1, chatManager.messages.value.size)
        assertEquals("What is my passport number?", chatManager.messages.value[0].content)
        assertEquals(userMsgId, chatManager.messages.value[0].id)

        chatManager.addAssistantMessage("Your passport number is [REDACTED].")
        assertEquals(2, chatManager.messages.value.size)
        assertEquals("Your passport number is [REDACTED].", chatManager.messages.value[1].content)

        // Clear chat discards all memory state
        chatManager.clearChat()
        assertTrue(chatManager.messages.value.isEmpty())
    }
}
