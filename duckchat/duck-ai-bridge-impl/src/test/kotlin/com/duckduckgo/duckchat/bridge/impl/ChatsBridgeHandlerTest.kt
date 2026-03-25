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

import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.bridge.impl.handler.ChatsBridgeHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatsDao
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ChatsBridgeHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val dao: DuckAiBridgeChatsDao = mock()
    private val handler by lazy {
        ChatsBridgeHandler(dao, coroutineTestRule.testScope, coroutineTestRule.testDispatcherProvider)
    }

    private val sampleJson = """{"chatId":"chat-1","messages":[]}"""

    // --- Existing business logic tests ---

    @Test
    fun `getAllChats returns all chats as JSON array`() {
        whenever(dao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity(chatId = "chat-1", data = sampleJson)))
        val result = JSONArray(handler.handleGetAll())
        assertEquals(1, result.length())
        assertEquals("chat-1", result.getJSONObject(0).getString("chatId"))
    }

    @Test
    fun `getAllChats returns empty array when no chats`() {
        whenever(dao.getAll()).thenReturn(emptyList())
        assertEquals("[]", handler.handleGetAll())
    }

    @Test
    fun `putChat calls dao upsert`() {
        handler.handlePut("chat-1", sampleJson)
        verify(dao).upsert(argThat { chatId == "chat-1" && data == sampleJson })
    }

    @Test
    fun `putChat with blank chatId does nothing`() {
        handler.handlePut("", sampleJson)
        verifyNoInteractions(dao)
    }

    @Test
    fun `putChat with invalid json does nothing`() {
        handler.handlePut("chat-1", "not-json")
        verifyNoInteractions(dao)
    }

    @Test
    fun `deleteChat calls dao delete`() {
        handler.handleDelete("chat-1")
        verify(dao).delete("chat-1")
    }

    @Test
    fun `deleteChat with blank id does nothing`() {
        handler.handleDelete("")
        verifyNoInteractions(dao)
    }

    @Test
    fun `deleteAllChats calls dao deleteAll`() {
        handler.handleDeleteAll()
        verify(dao).deleteAll()
    }

    // --- New onMessage dispatch tests ---

    @Test
    fun `getAllChats action replies with json array containing all chats`() {
        whenever(dao.getAll()).thenReturn(listOf(DuckAiBridgeChatEntity(chatId = "chat-1", data = sampleJson)))
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getAllChats"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getAllChats" &&
                    JSONArray(json.getString("value")).getJSONObject(0).getString("chatId") == "chat-1"
            },
        )
    }

    @Test
    fun `putChat action stores chat and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"putChat","chatId":"chat-1","data":{"chatId":"chat-1","messages":[]}}""", replyProxy)

        verify(dao).upsert(argThat { chatId == "chat-1" })
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `putChat with blank chatId sends no reply and does not upsert`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"putChat","chatId":"","data":{"chatId":"","messages":[]}}""", replyProxy)

        verifyNoInteractions(dao)
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteChat action removes chat and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteChat","chatId":"chat-1"}""", replyProxy)

        verify(dao).delete("chat-1")
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteAllChats action clears all and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteAllChats"}""", replyProxy)

        verify(dao).deleteAll()
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `unknown action sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"unknown"}""", replyProxy)

        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `malformed JSON sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("not json", replyProxy)

        verifyNoInteractions(replyProxy)
    }
}
