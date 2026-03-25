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
import com.duckduckgo.duckchat.bridge.impl.handler.ImagesBridgeHandler
import dagger.Lazy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ImagesBridgeHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val handler by lazy {
        ImagesBridgeHandler(Lazy { tempFolder.root }, coroutineTestRule.testScope, coroutineTestRule.testDispatcherProvider)
    }

    private val sampleJson = """{"uuid":"abc-123","chatId":"chat-1","data":"data:image/jpeg;base64,/9j/abc"}"""

    // --- Existing business logic tests ---

    @Test
    fun `putImage stores file`() {
        handler.handlePut("abc-123", sampleJson)
        assertTrue(tempFolder.root.resolve("abc-123").exists())
    }

    @Test
    fun `getImage returns stored json`() {
        handler.handlePut("abc-123", sampleJson)
        assertEquals(sampleJson, handler.handleGet("abc-123"))
    }

    @Test
    fun `getImage returns null when not found`() {
        assertNull(handler.handleGet("missing-uuid"))
    }

    @Test
    fun `deleteImage removes file`() {
        handler.handlePut("abc-123", sampleJson)
        handler.handleDelete("abc-123")
        assertNull(handler.handleGet("abc-123"))
    }

    @Test
    fun `deleteAllImages removes all files`() {
        handler.handlePut("abc-123", sampleJson)
        handler.handlePut("def-456", sampleJson.replace("abc-123", "def-456"))
        handler.handleDeleteAll()
        assertNull(handler.handleGet("abc-123"))
        assertNull(handler.handleGet("def-456"))
    }

    @Test
    fun `listImages returns metadata array`() {
        handler.handlePut("abc-123", sampleJson)
        val result = JSONArray(handler.handleList())
        assertEquals(1, result.length())
        val obj = result.getJSONObject(0)
        assertEquals("abc-123", obj.getString("uuid"))
        assertEquals("chat-1", obj.getString("chatId"))
        assertTrue(obj.has("dataSize"))
        assertFalse(obj.has("data"))
    }

    @Test
    fun `putImage with path traversal uuid is ignored`() {
        handler.handlePut("../evil", sampleJson)
        assertFalse(tempFolder.root.parentFile?.resolve("evil")?.exists() ?: false)
    }

    @Test
    fun `getImage with path traversal uuid returns null`() {
        assertNull(handler.handleGet("../etc/passwd"))
    }

    @Test
    fun `putImage with invalid json is ignored`() {
        handler.handlePut("abc-123", "not-json")
        assertNull(handler.handleGet("abc-123"))
    }

    // --- New onMessage dispatch tests ---

    @Test
    fun `getImage action replies with stored json when found`() {
        handler.handlePut("abc-123", sampleJson)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getImage","uuid":"abc-123"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getImage" && json.getString("value") == sampleJson
            },
        )
    }

    @Test
    fun `getImage action replies with null value when not found`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getImage","uuid":"missing"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getImage" && json.isNull("value")
            },
        )
    }

    @Test
    fun `getImage action with path traversal uuid replies with null`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"getImage","uuid":"../etc/passwd"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "getImage" && json.isNull("value")
            },
        )
    }

    @Test
    fun `putImage action stores image and sends no reply`() {
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"putImage","uuid":"abc-123","data":{"uuid":"abc-123","chatId":"chat-1","data":"base64..."}}""", replyProxy)

        assertTrue(tempFolder.root.resolve("abc-123").exists())
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteImage action removes file and sends no reply`() {
        handler.handlePut("abc-123", sampleJson)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteImage","uuid":"abc-123"}""", replyProxy)

        assertNull(handler.handleGet("abc-123"))
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `deleteAllImages action removes all files and sends no reply`() {
        handler.handlePut("abc-123", sampleJson)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"deleteAllImages"}""", replyProxy)

        assertNull(handler.handleGet("abc-123"))
        verifyNoInteractions(replyProxy)
    }

    @Test
    fun `listImages action replies with metadata array`() {
        handler.handlePut("abc-123", sampleJson)
        val replyProxy: JavaScriptReplyProxy = mock()

        handler.onMessage("""{"action":"listImages"}""", replyProxy)

        verify(replyProxy).postMessage(
            argThat<String> {
                val json = JSONObject(this)
                json.getString("action") == "listImages" &&
                    JSONArray(json.getString("value")).getJSONObject(0).getString("uuid") == "abc-123"
            },
        )
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
