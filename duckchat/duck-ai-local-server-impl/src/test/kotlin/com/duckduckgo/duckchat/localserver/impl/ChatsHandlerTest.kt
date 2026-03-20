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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.duckchat.localserver.impl.handler.ChatsHandler
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatEntity
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatsHandlerTest {

    private val dao: DuckAiChatsDao = mock()
    private val handler = ChatsHandler(dao)

    @Test
    fun `GET chats returns all as JSON array`() {
        whenever(dao.getAll()).thenReturn(listOf(
            DuckAiChatEntity("chat-1", """{"chatId":"chat-1","title":"Hello"}"""),
            DuckAiChatEntity("chat-2", """{"chatId":"chat-2","title":"World"}"""),
        ))

        val response = handler.handle(Method.GET, "/chats", null)

        assertEquals(Status.OK, response.status)
        val array = JSONArray(response.data.bufferedReader().readText())
        assertEquals(2, array.length())
        assertEquals("Hello", array.getJSONObject(0).getString("title"))
        assertEquals("World", array.getJSONObject(1).getString("title"))
    }

    @Test
    fun `GET chats returns empty array when no chats`() {
        whenever(dao.getAll()).thenReturn(emptyList())

        val response = handler.handle(Method.GET, "/chats", null)

        assertEquals(Status.OK, response.status)
        val array = JSONArray(response.data.bufferedReader().readText())
        assertEquals(0, array.length())
    }

    @Test
    fun `PUT chat upserts by chatId from URI`() {
        val body = """{"chatId":"chat-1","title":"Hello"}"""

        val response = handler.handle(Method.PUT, "/chats/chat-1", body)

        verify(dao).upsert(DuckAiChatEntity(chatId = "chat-1", data = body))
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `PUT chat with blank chatId returns 400`() {
        val response = handler.handle(Method.PUT, "/chats/", """{"chatId":"x"}""")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT chat with blank body returns 400`() {
        val response = handler.handle(Method.PUT, "/chats/chat-1", "")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT chat with non-JSON body returns 400`() {
        val response = handler.handle(Method.PUT, "/chats/chat-1", "not-json")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `DELETE chat by id returns 204`() {
        val response = handler.handle(Method.DELETE, "/chats/chat-1", null)
        verify(dao).delete("chat-1")
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE all chats returns 204`() {
        val response = handler.handle(Method.DELETE, "/chats", null)
        verify(dao).deleteAll()
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `unsupported method returns 405`() {
        val response = handler.handle(Method.POST, "/chats", null)
        assertEquals(Status.METHOD_NOT_ALLOWED, response.status)
    }

    @Test
    fun `PUT 204 response has non-null mime type`() {
        val body = """{"chatId":"chat-1"}"""
        val response = handler.handle(Method.PUT, "/chats/chat-1", body)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `DELETE 204 response has non-null mime type`() {
        val response = handler.handle(Method.DELETE, "/chats/chat-1", null)
        assertNotNull(response.mimeType)
    }
}
