/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.store.impl.handler.DuckAiNativeStorageJsMessageHandler
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import dagger.Lazy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

// SharedPreferencesProvider backed by InMemorySharedPreferences for use in DuckAiMigrationPrefs
private fun fakePrefsProvider(prefs: InMemorySharedPreferences): SharedPreferencesProvider =
    mock<SharedPreferencesProvider>().also { whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(prefs) }

class DuckAiNativeStorageJsMessageHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settingsDao: DuckAiBridgeSettingsDao = mock()
    private val chatsDao: DuckAiBridgeChatsDao = mock()
    private val fileMetaDao: DuckAiBridgeFileMetaDao = mock()
    private val fakePrefs = InMemorySharedPreferences()
    private val migrationPrefs = DuckAiMigrationPrefs(fakePrefsProvider(fakePrefs))
    private val pixels: DuckAiNativeStoragePixels = mock()
    private val hostProvider: DuckAiHostProvider = mock<DuckAiHostProvider>().also {
        whenever(it.getHost()).thenReturn("duck.ai")
    }
    private val handlerPlugin by lazy {
        DuckAiNativeStorageJsMessageHandler(settingsDao, chatsDao, fileMetaDao, Lazy { tempFolder.root }, hostProvider, migrationPrefs, pixels)
    }
    private val handler by lazy { handlerPlugin.getJsMessageHandler() }
    private val jsMessaging: JsMessaging = mock()

    // --- metadata ---

    @Test
    fun `featureName is duckAiNativeStorage`() {
        assertEquals("duckAiNativeStorage", handler.featureName)
    }

    @Test
    fun `allowedDomains contains duckduckgo dot com and duck dot ai`() {
        assertTrue(handler.allowedDomains.contains("duck.ai"))
    }

    @Test
    fun `methods list contains all expected methods`() {
        val expected = listOf(
            "getEntry", "putEntry", "getAllEntries", "replaceAllEntries", "deleteEntry", "deleteAllEntries",
            "getChat", "getAllChats", "putChat", "putChats", "migrateChats", "deleteChat", "deleteAllChats",
            "isMigrationDone", "markMigrationDone",
            "getFile", "putFile", "deleteFile", "deleteFiles", "deleteAllFiles", "listFiles",
        )
        assertTrue(handler.methods.containsAll(expected))
    }

    // --- getEntry / putEntry / getAllEntries / replaceAllEntries / deleteEntry / deleteAllEntries ---

    @Test
    fun `getEntry replies with value when key exists`() {
        whenever(settingsDao.get("theme")).thenReturn(DuckAiBridgeSettingEntity("theme", "dark"))

        handler.process(jsMessage("getEntry", """{"key":"theme"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.getString("value") == "dark" })
    }

    @Test
    fun `putEntry stores value in dao`() {
        handler.process(jsMessage("putEntry", """{"key":"theme","value":"dark"}"""), jsMessaging, null)

        verify(settingsDao).upsert(argThat { key == "theme" && value == "dark" })
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `getAllEntries replies with entries key`() {
        whenever(settingsDao.getAll()).thenReturn(listOf(DuckAiBridgeSettingEntity("theme", "dark")))

        handler.process(jsMessage("getAllEntries", "{}", id = "r2"), jsMessaging, null)

        verify(jsMessaging).onResponse(
            argThat { params.getJSONObject("entries").getString("theme") == "dark" },
        )
    }

    @Test
    fun `replaceAllEntries reads from entries key in params`() {
        handler.process(jsMessage("replaceAllEntries", """{"entries":{"theme":"dark","lang":"en"}}"""), jsMessaging, null)

        verify(settingsDao).replaceAll(argThat { size == 2 })
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `replaceAllEntries with missing entries field does nothing`() {
        handler.process(jsMessage("replaceAllEntries", "{}"), jsMessaging, null)

        verifyNoInteractions(settingsDao)
    }

    @Test
    fun `replaceAllEntries with empty entries object does nothing`() {
        handler.process(jsMessage("replaceAllEntries", """{"entries":{}}"""), jsMessaging, null)

        verifyNoInteractions(settingsDao)
    }

    @Test
    fun `deleteEntry calls dao delete`() {
        handler.process(jsMessage("deleteEntry", """{"key":"theme"}"""), jsMessaging, null)

        verify(settingsDao).delete("theme")
    }

    @Test
    fun `deleteAllEntries calls dao deleteAll`() {
        handler.process(jsMessage("deleteAllEntries", "{}"), jsMessaging, null)

        verify(settingsDao).deleteAll()
    }

    // --- getChat ---

    @Test
    fun `getChat replies with chat data when chatId exists`() {
        val chatJson = """{"chatId":"chat-1","messages":[]}"""
        whenever(chatsDao.getById("chat-1")).thenReturn(DuckAiBridgeChatEntity("chat-1", chatJson))

        handler.process(jsMessage("getChat", """{"chatId":"chat-1"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(
            argThat {
                method == "getChat" && id == "r1" &&
                    params.getJSONObject("chat").getString("chatId") == "chat-1"
            },
        )
    }

    @Test
    fun `getChat replies with null chat when chatId not found`() {
        whenever(chatsDao.getById("missing")).thenReturn(null)

        handler.process(jsMessage("getChat", """{"chatId":"missing"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { method == "getChat" && params.isNull("chat") })
    }

    @Test
    fun `getChat replies with null chat when chatId is blank`() {
        handler.process(jsMessage("getChat", """{"chatId":""}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.isNull("chat") })
        verifyNoInteractions(chatsDao)
    }

    @Test
    fun `getChat with no id sends no reply`() {
        whenever(chatsDao.getById(any())).thenReturn(null)

        handler.process(jsMessage("getChat", """{"chatId":"chat-1"}""", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- getAllChats ---

    @Test
    fun `getAllChats replies with chats array`() {
        val chatJson = """{"chatId":"chat-1","messages":[]}"""
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("chat-1", chatJson)))

        handler.process(jsMessage("getAllChats", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(
            argThat {
                val chats = params.getJSONArray("chats")
                chats.length() == 1 && chats.getJSONObject(0).getString("chatId") == "chat-1"
            },
        )
    }

    @Test
    fun `getAllChats with no id sends no reply`() {
        whenever(chatsDao.getAll()).thenReturn(emptyList())

        handler.process(jsMessage("getAllChats", "{}", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- putChat ---

    @Test
    fun `putChat stores chat in dao`() {
        handler.process(
            jsMessage("putChat", """{"chatId":"chat-1","data":{"chatId":"chat-1","messages":[]}}"""),
            jsMessaging,
            null,
        )

        verify(chatsDao).upsert(argThat { chatId == "chat-1" })
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `putChat with blank chatId does nothing`() {
        handler.process(jsMessage("putChat", """{"chatId":"","data":{}}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    @Test
    fun `putChat with missing data field does nothing`() {
        handler.process(jsMessage("putChat", """{"chatId":"chat-1"}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    // --- putChats ---

    @Test
    fun `putChats stores all chats in dao`() {
        handler.process(
            jsMessage(
                "putChats",
                """{"chats":[{"chatId":"chat-1","data":{"chatId":"chat-1","messages":[]}}""" +
                    """,{"chatId":"chat-2","data":{"chatId":"chat-2","messages":[]}}]}""",
            ),
            jsMessaging,
            null,
        )

        verify(chatsDao).upsertAll(
            argThat { size == 2 && this[0].chatId == "chat-1" && this[1].chatId == "chat-2" },
        )
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `putChats skips entries with blank chatId`() {
        handler.process(
            jsMessage("putChats", """{"chats":[{"chatId":"","data":{}},{"chatId":"chat-1","data":{"chatId":"chat-1"}}]}"""),
            jsMessaging,
            null,
        )

        verify(chatsDao).upsertAll(argThat { size == 1 && this[0].chatId == "chat-1" })
    }

    @Test
    fun `putChats skips entries with missing data field`() {
        handler.process(
            jsMessage("putChats", """{"chats":[{"chatId":"chat-1"},{"chatId":"chat-2","data":{"chatId":"chat-2"}}]}"""),
            jsMessaging,
            null,
        )

        verify(chatsDao).upsertAll(argThat { size == 1 && this[0].chatId == "chat-2" })
    }

    @Test
    fun `putChats with empty array does nothing`() {
        handler.process(jsMessage("putChats", """{"chats":[]}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    @Test
    fun `putChats with missing chats field does nothing`() {
        handler.process(jsMessage("putChats", """{}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    // --- migrateChats ---

    @Test
    fun `migrateChats stores all chats the same as putChats`() {
        handler.process(
            jsMessage(
                "migrateChats",
                """{"chats":[{"chatId":"chat-1","data":{"chatId":"chat-1","messages":[]}}]}""",
            ),
            jsMessaging,
            null,
        )

        verify(chatsDao).upsertAll(argThat { size == 1 && this[0].chatId == "chat-1" })
    }

    @Test
    fun `migrateChats with empty array does nothing`() {
        handler.process(jsMessage("migrateChats", """{"chats":[]}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    // --- deleteChat ---

    @Test
    fun `deleteChat calls dao delete`() {
        handler.process(jsMessage("deleteChat", """{"chatId":"chat-1"}"""), jsMessaging, null)

        verify(chatsDao).delete("chat-1")
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteChat with blank id does nothing`() {
        handler.process(jsMessage("deleteChat", """{"chatId":""}"""), jsMessaging, null)

        verifyNoInteractions(chatsDao)
    }

    // --- deleteAllChats ---

    @Test
    fun `deleteAllChats calls dao deleteAll`() {
        handler.process(jsMessage("deleteAllChats", "{}"), jsMessaging, null)

        verify(chatsDao).deleteAll()
        verifyNoInteractions(jsMessaging)
    }

    // --- isMigrationDone ---

    @Test
    fun `isMigrationDone replies false when key not set`() {
        handler.process(jsMessage("isMigrationDone", """{"key":"chats"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { method == "isMigrationDone" && id == "r1" && !params.getBoolean("value") })
    }

    @Test
    fun `isMigrationDone replies true when key is set`() {
        fakePrefs.edit().putBoolean(DuckAiMigrationPrefs.CHATS_KEY, true).commit()

        handler.process(jsMessage("isMigrationDone", """{"key":"chats"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.getBoolean("value") })
    }

    @Test
    fun `isMigrationDone replies false when no key provided`() {
        handler.process(jsMessage("isMigrationDone", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { !params.getBoolean("value") })
    }

    @Test
    fun `isMigrationDone with no id sends no reply`() {
        handler.process(jsMessage("isMigrationDone", """{"key":"chats"}""", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- markMigrationDone ---

    @Test
    fun `markMigrationDone writes key to prefs and sends no reply`() {
        handler.process(jsMessage("markMigrationDone", """{"key":"chats"}"""), jsMessaging, null)

        assertTrue(fakePrefs.getBoolean(DuckAiMigrationPrefs.CHATS_KEY, false))
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `markMigrationDone with no key does nothing`() {
        handler.process(jsMessage("markMigrationDone", "{}"), jsMessaging, null)

        assertFalse(fakePrefs.getBoolean(DuckAiMigrationPrefs.CHATS_KEY, false))
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `markMigrationDone fires pixel with the migration key`() {
        handler.process(jsMessage("markMigrationDone", """{"key":"chats"}"""), jsMessaging, null)

        verify(pixels).reportMigrationDone("chats")
    }

    @Test
    fun `markMigrationDone with no key fires blank key pixel`() {
        handler.process(jsMessage("markMigrationDone", "{}"), jsMessaging, null)

        verify(pixels).reportMigrationDoneBlankKey()
    }

    // --- putFile / getFile ---

    private val sampleParams =
        """{"uuid":"abc-123","chatId":"chat-1","fileName":"img.jpg","mimeType":"image/jpeg","data":"data:image/jpeg;base64,/9j/abc"}"""

    @Test
    fun `putFile stores file named by uuid`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)

        assertTrue(tempFolder.root.resolve("abc-123").exists())
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `putFile with path traversal uuid is ignored`() {
        handler.process(jsMessage("putFile", """{"uuid":"../evil","chatId":"c","data":"x"}"""), jsMessaging, null)

        assertFalse(tempFolder.root.parentFile?.resolve("evil")?.exists() ?: false)
    }

    @Test
    fun `putFile upserts metadata row in dao`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)

        verify(fileMetaDao).upsert(
            argThat { uuid == "abc-123" && chatId == "chat-1" && fileName == "img.jpg" && mimeType == "image/jpeg" },
        )
    }

    @Test
    fun `putFile with path traversal uuid does not write to dao`() {
        handler.process(jsMessage("putFile", """{"uuid":"../evil","chatId":"c","data":"x"}"""), jsMessaging, null)

        verifyNoInteractions(fileMetaDao)
    }

    @Test
    fun `getFile replies with stored file object`() {
        tempFolder.root.resolve("abc-123").writeText(sampleParams)

        handler.process(jsMessage("getFile", """{"uuid":"abc-123"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(
            argThat {
                method == "getFile" && id == "r1" &&
                    params.getString("uuid") == "abc-123" &&
                    params.getString("chatId") == "chat-1"
            },
        )
    }

    @Test
    fun `getFile replies with null value when uuid not found`() {
        handler.process(jsMessage("getFile", """{"uuid":"missing"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { method == "getFile" && params.isNull("value") })
    }

    @Test
    fun `getFile with path traversal uuid replies with null value`() {
        handler.process(jsMessage("getFile", """{"uuid":"../etc/passwd"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.isNull("value") })
    }

    @Test
    fun `getFile with no id sends no reply`() {
        handler.process(jsMessage("getFile", """{"uuid":"abc-123"}""", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `getFile replies with null value when file content is corrupted JSON`() {
        tempFolder.root.resolve("abc-123").writeText("not valid json {{{{")

        handler.process(jsMessage("getFile", """{"uuid":"abc-123"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { method == "getFile" && params.isNull("value") })
    }

    // --- deleteFile / deleteFiles / deleteAllFiles ---

    @Test
    fun `deleteFile removes file`() {
        tempFolder.root.resolve("abc-123").writeText("content")

        handler.process(jsMessage("deleteFile", """{"uuid":"abc-123"}"""), jsMessaging, null)

        assertFalse(tempFolder.root.resolve("abc-123").exists())
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteFile removes metadata row from dao`() {
        handler.process(jsMessage("deleteFile", """{"uuid":"abc-123"}"""), jsMessaging, null)

        verify(fileMetaDao).delete("abc-123")
    }

    @Test
    fun `deleteFile with path traversal uuid does not touch dao`() {
        handler.process(jsMessage("deleteFile", """{"uuid":"../evil"}"""), jsMessaging, null)

        verifyNoInteractions(fileMetaDao)
    }

    @Test
    fun `deleteFiles removes files from disk for the given chatId`() {
        val fileForChat = tempFolder.root.resolve("file-chat1")
        val fileForOtherChat = tempFolder.root.resolve("file-chat2")
        fileForChat.writeText("content")
        fileForOtherChat.writeText("content")
        whenever(fileMetaDao.getByChatId("chat-1")).thenReturn(
            listOf(DuckAiBridgeFileMetaEntity(uuid = "file-chat1", chatId = "chat-1", fileName = "a.jpg", mimeType = "image/jpeg")),
        )

        handler.process(jsMessage("deleteFiles", """{"chatId":"chat-1"}"""), jsMessaging, null)

        assertFalse(fileForChat.exists())
        assertTrue(fileForOtherChat.exists())
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteFiles calls deleteByChatId on dao`() {
        whenever(fileMetaDao.getByChatId("chat-1")).thenReturn(emptyList())

        handler.process(jsMessage("deleteFiles", """{"chatId":"chat-1"}"""), jsMessaging, null)

        verify(fileMetaDao).deleteByChatId("chat-1")
    }

    @Test
    fun `deleteFiles with blank chatId does nothing`() {
        handler.process(jsMessage("deleteFiles", """{"chatId":""}"""), jsMessaging, null)

        verifyNoInteractions(fileMetaDao)
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteAllFiles removes all files`() {
        tempFolder.root.resolve("abc-123").writeText("content")

        handler.process(jsMessage("deleteAllFiles", "{}"), jsMessaging, null)

        assertTrue(tempFolder.root.listFiles()?.isEmpty() ?: true)
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteAllFiles clears metadata table in dao`() {
        handler.process(jsMessage("deleteAllFiles", "{}"), jsMessaging, null)

        verify(fileMetaDao).deleteAll()
    }

    // --- listFiles ---

    @Test
    fun `listFiles replies with metadata from dao`() {
        whenever(fileMetaDao.getAll()).thenReturn(
            listOf(DuckAiBridgeFileMetaEntity(uuid = "abc-123", chatId = "chat-1", fileName = "img.jpg", mimeType = "image/jpeg")),
        )

        handler.process(jsMessage("listFiles", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(
            argThat {
                val files = params.getJSONArray("files")
                files.length() == 1 &&
                    files.getJSONObject(0).getString("uuid") == "abc-123" &&
                    files.getJSONObject(0).getString("chatId") == "chat-1" &&
                    files.getJSONObject(0).getString("fileName") == "img.jpg" &&
                    files.getJSONObject(0).getString("mimeType") == "image/jpeg" &&
                    files.getJSONObject(0).getLong("dataSize") == 0L &&
                    !files.getJSONObject(0).has("data")
            },
        )
    }

    @Test
    fun `listFiles with no id sends no reply`() {
        whenever(fileMetaDao.getAll()).thenReturn(emptyList())

        handler.process(jsMessage("listFiles", "{}", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- unknown method ---

    @Test
    fun `unknown method does nothing`() {
        handler.process(jsMessage("unknownMethod", "{}"), jsMessaging, null)

        verifyNoInteractions(settingsDao)
        verifyNoInteractions(chatsDao)
        verifyNoInteractions(fileMetaDao)
        verifyNoInteractions(jsMessaging)
    }

    // --- helpers ---

    private fun jsMessage(method: String, paramsJson: String, id: String? = null): JsMessage =
        JsMessage("contentScopeScripts", "duckAiNativeStorage", method, JSONObject(paramsJson), id)
}
