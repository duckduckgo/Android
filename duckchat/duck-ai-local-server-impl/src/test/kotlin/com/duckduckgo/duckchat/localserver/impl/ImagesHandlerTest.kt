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

import com.duckduckgo.duckchat.localserver.impl.handler.ImagesHandler
import dagger.Lazy
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagesHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val handler by lazy { ImagesHandler(Lazy { tempFolder.root }) }

    private val sampleBody = """{"uuid":"abc-123","chatId":"chat-1","data":"data:image/jpeg;base64,/9j/abc"}"""

    @Test
    fun `PUT stores image file and returns 204`() {
        val response = handler.handle(Method.PUT, "/images/abc-123", sampleBody)
        assertEquals(Status.NO_CONTENT, response.status)
        assertTrue(tempFolder.root.resolve("abc-123").exists())
    }

    @Test
    fun `GET returns stored image JSON`() {
        handler.handle(Method.PUT, "/images/abc-123", sampleBody)

        val response = handler.handle(Method.GET, "/images/abc-123", null)

        assertEquals(Status.OK, response.status)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertEquals("abc-123", json.getString("uuid"))
        assertEquals("chat-1", json.getString("chatId"))
    }

    @Test
    fun `GET returns 404 for missing image`() {
        val response = handler.handle(Method.GET, "/images/missing", null)
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `PUT is idempotent — second PUT overwrites`() {
        handler.handle(Method.PUT, "/images/abc-123", sampleBody)
        val updated = """{"uuid":"abc-123","chatId":"chat-1","data":"data:image/jpeg;base64,UPDATED"}"""
        handler.handle(Method.PUT, "/images/abc-123", updated)

        val response = handler.handle(Method.GET, "/images/abc-123", null)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertEquals("UPDATED", json.getString("data").substringAfterLast(","))
    }

    @Test
    fun `DELETE removes image file`() {
        handler.handle(Method.PUT, "/images/abc-123", sampleBody)
        val response = handler.handle(Method.DELETE, "/images/abc-123", null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertFalse(tempFolder.root.resolve("abc-123").exists())
    }

    @Test
    fun `DELETE is idempotent — deleting non-existent returns 204`() {
        val response = handler.handle(Method.DELETE, "/images/nonexistent", null)
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE all removes all files`() {
        handler.handle(Method.PUT, "/images/abc-1", sampleBody)
        handler.handle(Method.PUT, "/images/abc-2", sampleBody)
        val response = handler.handle(Method.DELETE, "/images", null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertEquals(0, tempFolder.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `PUT with blank body returns 400`() {
        val response = handler.handle(Method.PUT, "/images/abc-123", "")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT with non-JSON body returns 400`() {
        val response = handler.handle(Method.PUT, "/images/abc-123", "not-json")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT with path-traversal uuid returns 400`() {
        val response = handler.handle(Method.PUT, "/images/../evil", sampleBody)
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT with single dot uuid returns 400`() {
        val response = handler.handle(Method.PUT, "/images/.", sampleBody)
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `GET images returns metadata array without data field`() {
        handler.handle(Method.PUT, "/images/abc-123", sampleBody)
        handler.handle(Method.PUT, "/images/abc-456", """{"uuid":"abc-456","chatId":"chat-2","data":"data:image/png;base64,abc"}""")

        val response = handler.handle(Method.GET, "/images", null)

        assertEquals(Status.OK, response.status)
        val array = org.json.JSONArray(response.data.bufferedReader().readText())
        assertEquals(2, array.length())
        // metadata fields present
        assertNotNull(array.getJSONObject(0).getString("uuid"))
        assertNotNull(array.getJSONObject(0).getString("chatId"))
        assertTrue(array.getJSONObject(0).getInt("dataSize") > 0)
        // data field NOT present
        assertFalse(array.getJSONObject(0).has("data"))
    }

    @Test
    fun `GET images returns empty array when no images`() {
        val response = handler.handle(Method.GET, "/images", null)
        assertEquals(Status.OK, response.status)
        val array = org.json.JSONArray(response.data.bufferedReader().readText())
        assertEquals(0, array.length())
    }

    @Test
    fun `unsupported method on collection returns 405`() {
        val response = handler.handle(Method.POST, "/images", null)
        assertEquals(Status.METHOD_NOT_ALLOWED, response.status)
    }

    @Test
    fun `unsupported method on single resource returns 405`() {
        val response = handler.handle(Method.POST, "/images/abc-123", sampleBody)
        assertEquals(Status.METHOD_NOT_ALLOWED, response.status)
    }

    @Test
    fun `PUT 204 response has non-null mime type`() {
        val response = handler.handle(Method.PUT, "/images/abc-123", sampleBody)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `DELETE 204 response has non-null mime type`() {
        val response = handler.handle(Method.DELETE, "/images/abc-123", null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }
}
