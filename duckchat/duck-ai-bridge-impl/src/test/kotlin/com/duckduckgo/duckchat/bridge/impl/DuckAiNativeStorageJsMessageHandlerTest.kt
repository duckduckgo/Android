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

package com.duckduckgo.duckchat.bridge.impl

import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.bridge.impl.handler.DuckAiNativeStorageJsMessageHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DuckAiNativeStorageJsMessageHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settingsDao: DuckAiBridgeSettingsDao = mock()
    private val chatsDao: DuckAiBridgeChatsDao = mock()
    private val hostProvider: DuckAiHostProvider = mock<DuckAiHostProvider>().also {
        whenever(it.getHost()).thenReturn("duck.ai")
    }
    private val handlerPlugin by lazy {
        DuckAiNativeStorageJsMessageHandler(settingsDao, chatsDao, Lazy { tempFolder.root }, hostProvider)
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
            "getSetting", "putSetting", "getAllSettings", "replaceAllSettings", "deleteSetting", "deleteAllSettings",
            "getAllChats", "putChat", "deleteChat", "deleteAllChats",
            "isMigrationDone", "markMigrationDone",
            "getFile", "putFile", "deleteFile", "deleteAllFiles", "listFiles",
        )
        assertTrue(handler.methods.containsAll(expected))
    }

    // --- getSetting ---

    @Test
    fun `getSetting replies with value when key exists`() {
        whenever(settingsDao.get("theme")).thenReturn(DuckAiBridgeSettingEntity("theme", "dark"))

        handler.process(jsMessage("getSetting", """{"key":"theme"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            featureName == "duckAiNativeStorage" && method == "getSetting" && id == "r1" &&
                params.getString("value") == "dark"
        })
    }

    @Test
    fun `getSetting replies with null value when key missing`() {
        whenever(settingsDao.get("theme")).thenReturn(null)

        handler.process(jsMessage("getSetting", """{"key":"theme"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.isNull("value") })
    }

    @Test
    fun `getSetting with no id sends no reply`() {
        whenever(settingsDao.get("theme")).thenReturn(DuckAiBridgeSettingEntity("theme", "dark"))

        handler.process(jsMessage("getSetting", """{"key":"theme"}""", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- putSetting ---

    @Test
    fun `putSetting stores value in dao`() {
        handler.process(jsMessage("putSetting", """{"key":"theme","value":"dark"}"""), jsMessaging, null)

        verify(settingsDao).upsert(argThat { key == "theme" && value == "dark" })
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `putSetting with blank key does nothing`() {
        handler.process(jsMessage("putSetting", """{"key":"","value":"dark"}"""), jsMessaging, null)

        verifyNoInteractions(settingsDao)
    }

    // --- getAllSettings ---

    @Test
    fun `getAllSettings replies with settings object`() {
        whenever(settingsDao.getAll()).thenReturn(listOf(DuckAiBridgeSettingEntity("theme", "dark")))

        handler.process(jsMessage("getAllSettings", "{}", id = "r2"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            params.getJSONObject("settings").getString("theme") == "dark"
        })
    }

    // --- replaceAllSettings ---

    @Test
    fun `replaceAllSettings calls dao replaceAll`() {
        handler.process(jsMessage("replaceAllSettings", """{"settings":{"theme":"dark","lang":"en"}}"""), jsMessaging, null)

        verify(settingsDao).replaceAll(argThat { size == 2 })
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `replaceAllSettings with missing settings field does nothing`() {
        handler.process(jsMessage("replaceAllSettings", "{}"), jsMessaging, null)

        verifyNoInteractions(settingsDao)
    }

    // --- deleteSetting ---

    @Test
    fun `deleteSetting calls dao delete`() {
        handler.process(jsMessage("deleteSetting", """{"key":"theme"}"""), jsMessaging, null)

        verify(settingsDao).delete("theme")
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteSetting with blank key does nothing`() {
        handler.process(jsMessage("deleteSetting", """{"key":""}"""), jsMessaging, null)

        verifyNoInteractions(settingsDao)
    }

    // --- deleteAllSettings ---

    @Test
    fun `deleteAllSettings calls dao deleteAll`() {
        handler.process(jsMessage("deleteAllSettings", "{}"), jsMessaging, null)

        verify(settingsDao).deleteAll()
        verifyNoInteractions(jsMessaging)
    }

    // --- getAllChats ---

    @Test
    fun `getAllChats replies with chats array`() {
        val chatJson = """{"chatId":"chat-1","messages":[]}"""
        whenever(chatsDao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity("chat-1", chatJson)))

        handler.process(jsMessage("getAllChats", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            val chats = params.getJSONArray("chats")
            chats.length() == 1 && chats.getJSONObject(0).getString("chatId") == "chat-1"
        })
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
            jsMessaging, null,
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
    fun `isMigrationDone replies false when migration key not in dao`() {
        whenever(settingsDao.get("__duckAiMigrationDone")).thenReturn(null)

        handler.process(jsMessage("isMigrationDone", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            method == "isMigrationDone" && id == "r1" && !params.getBoolean("value")
        })
    }

    @Test
    fun `isMigrationDone replies true when migration key is in dao`() {
        whenever(settingsDao.get("__duckAiMigrationDone")).thenReturn(
            DuckAiBridgeSettingEntity("__duckAiMigrationDone", "true"),
        )

        handler.process(jsMessage("isMigrationDone", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat { params.getBoolean("value") })
    }

    @Test
    fun `isMigrationDone with no id sends no reply`() {
        whenever(settingsDao.get("__duckAiMigrationDone")).thenReturn(null)

        handler.process(jsMessage("isMigrationDone", "{}", id = null), jsMessaging, null)

        verifyNoInteractions(jsMessaging)
    }

    // --- markMigrationDone ---

    @Test
    fun `markMigrationDone upserts migration key and sends no reply`() {
        handler.process(jsMessage("markMigrationDone", "{}"), jsMessaging, null)

        verify(settingsDao).upsert(argThat { key == "__duckAiMigrationDone" && value == "true" })
        verifyNoInteractions(jsMessaging)
    }

    // --- putFile / getFile ---

    private val sampleParams = """{"uuid":"abc-123","chatId":"chat-1","data":"data:image/jpeg;base64,/9j/abc"}"""

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
    fun `getFile replies with stored file object`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)

        handler.process(jsMessage("getFile", """{"uuid":"abc-123"}""", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            method == "getFile" && id == "r1" &&
                params.getString("uuid") == "abc-123" &&
                params.getString("chatId") == "chat-1"
        })
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

    // --- deleteFile / deleteAllFiles ---

    @Test
    fun `deleteFile removes file`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)
        handler.process(jsMessage("deleteFile", """{"uuid":"abc-123"}"""), jsMessaging, null)

        assertFalse(tempFolder.root.resolve("abc-123").exists())
        verifyNoInteractions(jsMessaging)
    }

    @Test
    fun `deleteAllFiles removes all files`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)
        handler.process(jsMessage("deleteAllFiles", "{}"), jsMessaging, null)

        assertTrue(tempFolder.root.listFiles()?.isEmpty() ?: true)
        verifyNoInteractions(jsMessaging)
    }

    // --- listFiles ---

    @Test
    fun `listFiles replies with metadata array`() {
        handler.process(jsMessage("putFile", sampleParams), jsMessaging, null)

        handler.process(jsMessage("listFiles", "{}", id = "r1"), jsMessaging, null)

        verify(jsMessaging).onResponse(argThat {
            val files = params.getJSONArray("files")
            files.length() == 1 &&
                files.getJSONObject(0).getString("uuid") == "abc-123" &&
                files.getJSONObject(0).getString("chatId") == "chat-1" &&
                files.getJSONObject(0).has("dataSize") &&
                !files.getJSONObject(0).has("data")
        })
    }

    // --- unknown method ---

    @Test
    fun `unknown method does nothing`() {
        handler.process(jsMessage("unknownMethod", "{}"), jsMessaging, null)

        verifyNoInteractions(settingsDao)
        verifyNoInteractions(chatsDao)
        verifyNoInteractions(jsMessaging)
    }

    // --- helpers ---

    private fun jsMessage(method: String, paramsJson: String, id: String? = null): JsMessage =
        JsMessage("contentScopeScripts", "duckAiNativeStorage", method, JSONObject(paramsJson), id)
}
