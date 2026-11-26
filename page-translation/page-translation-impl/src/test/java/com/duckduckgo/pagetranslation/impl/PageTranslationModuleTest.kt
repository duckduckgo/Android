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

package com.duckduckgo.pagetranslation.impl

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PageTranslationModuleTest {
    private val server = MockWebServer()
    private val client = PageTranslationModule.provideModelsHttpClient()

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun sendGenericUserAgentWithoutCookiesOrLanguage() {
        server.enqueue(MockResponse().setHeader(SET_COOKIE_HEADER, TEST_COOKIE))
        server.enqueue(MockResponse())

        repeat(2) { client.newCall(Request.Builder().url(server.url("/")).build()).execute().close() }

        server.takeRequest(2, TimeUnit.SECONDS)
        val request = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(USER_AGENT_VALUE, request.getHeader(USER_AGENT_HEADER))
        assertNull(request.getHeader(COOKIE_HEADER))
        assertNull(request.getHeader(ACCEPT_LANGUAGE_HEADER))
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT_VALUE = "DuckDuckGo"
        const val SET_COOKIE_HEADER = "Set-Cookie"
        const val COOKIE_HEADER = "Cookie"
        const val ACCEPT_LANGUAGE_HEADER = "Accept-Language"
        const val TEST_COOKIE = "example=value; Path=/"
    }
}
