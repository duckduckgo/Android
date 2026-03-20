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

import com.duckduckgo.duckchat.localserver.impl.handler.SettingsHandler
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingEntity
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingsDao
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsHandlerTest {

    private val dao: DuckAiSettingsDao = mock()
    private val handler = SettingsHandler(dao)

    @Test
    fun `GET settings returns all as JSON`() {
        whenever(dao.getAll()).thenReturn(listOf(
            DuckAiSettingEntity("theme", "dark"),
            DuckAiSettingEntity("model", "gpt-4"),
        ))

        val response = handler.handle(Method.GET, uri = "/settings", body = null)

        assertEquals(Status.OK, response.status)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertEquals("dark", json.getString("theme"))
        assertEquals("gpt-4", json.getString("model"))
    }

    @Test
    fun `GET single key returns JSON string literal`() {
        whenever(dao.get("theme")).thenReturn(DuckAiSettingEntity("theme", "dark"))

        val response = handler.handle(Method.GET, uri = "/settings/theme", body = null)

        assertEquals(Status.OK, response.status)
        val responseBody = response.data.bufferedReader().readText()
        assertEquals("\"dark\"", responseBody)
    }

    @Test
    fun `GET single key returns 404 when key missing`() {
        whenever(dao.get("theme")).thenReturn(null)

        val response = handler.handle(Method.GET, uri = "/settings/theme", body = null)

        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `PUT settings upserts key from JSON string literal`() {
        val response = handler.handle(Method.PUT, uri = "/settings/theme", body = "\"dark\"")

        verify(dao).upsert(DuckAiSettingEntity(key = "theme", value = "dark"))
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `PUT single key with non-JSON body returns 400`() {
        val response = handler.handle(Method.PUT, uri = "/settings/theme", body = "dark")

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT settings bulk replaces all`() {
        val body = JSONObject().put("theme", "dark").put("model", "gpt-4").toString()

        val response = handler.handle(Method.PUT, uri = "/settings", body = body)

        assertEquals(Status.NO_CONTENT, response.status)
        verify(dao).replaceAll(argThat { entities ->
            entities.toSet() == setOf(
                DuckAiSettingEntity("theme", "dark"),
                DuckAiSettingEntity("model", "gpt-4"),
            )
        })
    }

    @Test
    fun `PUT settings bulk with invalid JSON returns 400`() {
        val response = handler.handle(Method.PUT, uri = "/settings", body = "not-json")

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `DELETE settings removes key`() {
        val response = handler.handle(Method.DELETE, uri = "/settings/theme", body = null)

        verify(dao).delete("theme")
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE settings deletes all`() {
        val response = handler.handle(Method.DELETE, uri = "/settings", body = null)

        verify(dao).deleteAll()
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `unknown method returns 405`() {
        val response = handler.handle(Method.POST, uri = "/settings", body = null)
        assertEquals(Status.METHOD_NOT_ALLOWED, response.status)
    }

    @Test
    fun `GET settings returns empty JSON when no settings`() {
        whenever(dao.getAll()).thenReturn(emptyList())

        val response = handler.handle(Method.GET, uri = "/settings", body = null)

        assertEquals(Status.OK, response.status)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertEquals(0, json.length())
    }

    @Test
    fun `PUT with blank key returns 400`() {
        val response = handler.handle(Method.PUT, uri = "/settings/", body = "\"value\"")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT with blank body returns 400`() {
        val response = handler.handle(Method.PUT, uri = "/settings/theme", body = "")
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `DELETE with blank key returns 400`() {
        val response = handler.handle(Method.DELETE, uri = "/settings/", body = null)
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    // --- Regression: NanoHTTPD ContentType(null) NPE ---
    // NanoHTTPD 2.3.1 throws NPE in ContentType constructor when mimeType is null.
    // All 204 responses must use a non-null mimeType to avoid ERR_INVALID_HTTP_RESPONSE.

    @Test
    fun `PUT 204 response has non-null mime type`() {
        val response = handler.handle(Method.PUT, uri = "/settings/theme", body = "\"dark\"")
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `PUT bulk 204 response has non-null mime type`() {
        val body = JSONObject().put("theme", "dark").toString()
        val response = handler.handle(Method.PUT, uri = "/settings", body = body)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `DELETE single key 204 response has non-null mime type`() {
        val response = handler.handle(Method.DELETE, uri = "/settings/theme", body = null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `DELETE all 204 response has non-null mime type`() {
        val response = handler.handle(Method.DELETE, uri = "/settings", body = null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }
}
