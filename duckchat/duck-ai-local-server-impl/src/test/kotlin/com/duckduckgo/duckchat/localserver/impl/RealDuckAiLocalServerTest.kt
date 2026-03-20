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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealDuckAiLocalServerTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val originValidator = OriginValidator(appBuildConfig)
    private val noHandlers = emptySet<DuckAiRequestHandler>()
    private val server = RealDuckAiLocalServer(originValidator, noHandlers, appBuildConfig)

    // --- CORS headers on allowed origin ---

    @Test
    fun `response includes CORS allow-origin header for allowed origin`() {
        val session = mockSession(Method.GET, "/unknown", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("https://duck.ai", response.getHeader("Access-Control-Allow-Origin"))
    }

    @Test
    fun `response includes CORS allow-methods header for allowed origin`() {
        val session = mockSession(Method.GET, "/unknown", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("GET, POST, PUT, DELETE, OPTIONS", response.getHeader("Access-Control-Allow-Methods"))
    }

    @Test
    fun `response includes CORS allow-headers header for allowed origin`() {
        val session = mockSession(Method.GET, "/unknown", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("Content-Type, Origin", response.getHeader("Access-Control-Allow-Headers"))
    }

    // --- OPTIONS preflight ---

    @Test
    fun `OPTIONS preflight returns 204`() {
        val session = mockSession(Method.OPTIONS, "/settings/theme", "https://duck.ai")
        val response = server.serve(session)
        assertEquals(Status.NO_CONTENT, response.status)
    }

    @Test
    fun `OPTIONS preflight includes Access-Control-Max-Age`() {
        val session = mockSession(Method.OPTIONS, "/settings/theme", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("86400", response.getHeader("Access-Control-Max-Age"))
    }

    @Test
    fun `OPTIONS preflight includes CORS allow-origin header`() {
        val session = mockSession(Method.OPTIONS, "/settings/theme", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("https://duck.ai", response.getHeader("Access-Control-Allow-Origin"))
    }

    // --- Forbidden (no CORS) ---

    @Test
    fun `rejected origin returns 403 with no CORS header`() {
        val session = mockSession(Method.GET, "/settings/theme", "https://evil.com")
        val response = server.serve(session)
        assertEquals(Status.FORBIDDEN, response.status)
        assertNull(response.getHeader("Access-Control-Allow-Origin"))
    }

    // --- Connection: close to avoid NanoHTTPD keep-alive bugs ---

    @Test
    fun `response includes Connection close header`() {
        val session = mockSession(Method.GET, "/unknown", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("close", response.getHeader("Connection"))
    }

    @Test
    fun `OPTIONS preflight includes Connection close header`() {
        val session = mockSession(Method.OPTIONS, "/settings/theme", "https://duck.ai")
        val response = server.serve(session)
        assertEquals("close", response.getHeader("Connection"))
    }

    // --- CORS echoes the requesting origin, not a wildcard ---

    @Test
    fun `CORS header echoes duckduckgo com origin`() {
        val session = mockSession(Method.GET, "/unknown", "https://duckduckgo.com")
        val response = server.serve(session)
        assertEquals("https://duckduckgo.com", response.getHeader("Access-Control-Allow-Origin"))
    }

    // --- Helpers ---

    private fun mockSession(method: Method, uri: String, origin: String): IHTTPSession {
        val session = mock<IHTTPSession>()
        whenever(session.method).thenReturn(method)
        whenever(session.uri).thenReturn(uri)
        whenever(session.headers).thenReturn(mapOf("origin" to origin))
        return session
    }
}
