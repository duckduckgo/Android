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

package com.duckduckgo.app.tabs.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealPageTitleFetcherTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val server = MockWebServer()
    private val userAgentProvider: UserAgentProvider = mock {
        on { userAgent(anyOrNull(), any()) } doReturn "test-ua"
    }
    private val gpc: Gpc = mock()
    private val testee = RealPageTitleFetcher(userAgentProvider, gpc, coroutineRule.testDispatcherProvider)

    @After
    fun after() {
        server.shutdown()
    }

    @Test
    fun whenHtmlPageThenTitleDecodedAndWhitespaceCollapsed() = runTest {
        server.enqueue(html("<html><head><TITLE lang=\"en\">\n  Tom &amp; Jerry   &#8211; News\n</TITLE></head>"))

        assertEquals("Tom & Jerry – News", testee.fetchTitle(server.url("/").toString()))
    }

    @Test
    fun whenHtmlPageThenRequestSentWithBrowserUserAgentAndGpcHeaders() = runTest {
        whenever(gpc.getHeaders(any())).thenReturn(mapOf("Sec-GPC" to "1"))
        server.enqueue(html("<title>Title</title>"))

        testee.fetchTitle(server.url("/").toString())

        val request = server.takeRequest()
        assertEquals("test-ua", request.getHeader("User-Agent"))
        assertEquals("1", request.getHeader("Sec-GPC"))
    }

    @Test
    fun whenNotHtmlThenNull() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/svg+xml").setBody("<svg><title>Icon</title></svg>"))

        assertNull(testee.fetchTitle(server.url("/").toString()))
    }

    @Test
    fun whenErrorStatusThenNull() = runTest {
        server.enqueue(html("<title>Just a moment...</title>").setResponseCode(403))

        assertNull(testee.fetchTitle(server.url("/").toString()))
    }

    @Test
    fun whenNoTitleThenNull() = runTest {
        server.enqueue(html("<html><head></head><body>no title</body></html>"))

        assertNull(testee.fetchTitle(server.url("/").toString()))
    }

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)
}
