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

import com.duckduckgo.duckchat.localserver.impl.handler.MigrationHandler
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiMigrationDao
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MigrationHandlerTest {

    private val dao: DuckAiMigrationDao = mock()
    private val handler = MigrationHandler(dao)

    @Test
    fun `GET migration returns done false when not migrated`() {
        whenever(dao.isDone()).thenReturn(false)

        val response = handler.handle(Method.GET, "/migration", null)

        assertEquals(Status.OK, response.status)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertFalse(json.getBoolean("done"))
    }

    @Test
    fun `GET migration returns done true when already migrated`() {
        whenever(dao.isDone()).thenReturn(true)

        val response = handler.handle(Method.GET, "/migration", null)

        assertEquals(Status.OK, response.status)
        val json = JSONObject(response.data.bufferedReader().readText())
        assertTrue(json.getBoolean("done"))
    }

    @Test
    fun `POST migration marks done and returns 204`() {
        val response = handler.handle(Method.POST, "/migration", null)

        verify(dao).markDone()
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `POST 204 response has non-null mime type`() {
        val response = handler.handle(Method.POST, "/migration", null)
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `POST migration is idempotent — second call also returns 204`() {
        handler.handle(Method.POST, "/migration", null)
        val response = handler.handle(Method.POST, "/migration", null)
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE migration resets done flag and returns 204`() {
        val response = handler.handle(Method.DELETE, "/migration", null)

        verify(dao).reset()
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE 204 response has non-null mime type`() {
        val response = handler.handle(Method.DELETE, "/migration", null)
        assertNotNull(response.mimeType)
    }

    @Test
    fun `unsupported method returns 405`() {
        val response = handler.handle(Method.PUT, "/migration", null)
        assertEquals(Status.METHOD_NOT_ALLOWED, response.status)
    }
}
