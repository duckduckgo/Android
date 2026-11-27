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

package com.duckduckgo.request.interception.impl

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.user.agent.api.UserAgentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RequestRewriteDecisionTest {

    private val mockGpc: Gpc = mock()
    private val mockUserAgentProvider: UserAgentProvider = mock()
    private val mockRequest: WebResourceRequest = mock()

    private val testee = RequestRewriteDecision(mockGpc, mockUserAgentProvider)

    private val urlString = "https://example.com/page"

    private fun givenRequest(
        isForMainFrame: Boolean = true,
        method: String = "GET",
        headers: MutableMap<String, String> = mutableMapOf(),
    ) {
        whenever(mockRequest.url).thenReturn(urlString.toUri())
        whenever(mockRequest.isForMainFrame).thenReturn(isForMainFrame)
        whenever(mockRequest.method).thenReturn(method)
        whenever(mockRequest.requestHeaders).thenReturn(headers)
    }

    @Test
    fun whenMainFrameGetAndUrlCanAddHeadersThenShouldAddGpcHeaders() {
        givenRequest()
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(true)

        assertTrue(testee.shouldAddGpcHeaders(mockRequest))
    }

    @Test
    fun whenUrlCannotAddHeadersThenShouldNotAddGpcHeaders() {
        givenRequest()
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(false)

        assertFalse(testee.shouldAddGpcHeaders(mockRequest))
    }

    @Test
    fun whenRequestIsNotForMainFrameThenShouldNotAddGpcHeaders() {
        givenRequest(isForMainFrame = false)
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(true)

        assertFalse(testee.shouldAddGpcHeaders(mockRequest))
    }

    @Test
    fun whenRequestMethodIsNotGetThenShouldNotAddGpcHeaders() {
        givenRequest(method = "POST")
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(true)

        assertFalse(testee.shouldAddGpcHeaders(mockRequest))
    }

    @Test
    fun whenGetHeadersThenGpcHeadersAreMergedIntoRequestHeaders() {
        val headers = mutableMapOf("Accept" to "text/html")
        givenRequest(headers = headers)
        whenever(mockGpc.getHeaders(urlString)).thenReturn(mapOf("Sec-GPC" to "1"))

        val result = testee.getHeaders(mockRequest)

        assertEquals(mapOf("Accept" to "text/html", "Sec-GPC" to "1"), result)
    }

    @Test
    fun whenGetHeadersThenRequestHeadersAreMutated() {
        val headers = mutableMapOf("Accept" to "text/html")
        givenRequest(headers = headers)
        whenever(mockGpc.getHeaders(urlString)).thenReturn(mapOf("Sec-GPC" to "1"))

        testee.getHeaders(mockRequest)

        assertEquals("1", headers["Sec-GPC"])
    }

    @Test
    fun whenUserAgentRequestedThenProviderValueIsReturned() {
        whenever(mockUserAgentProvider.userAgent(urlString, true)).thenReturn("desktop-agent")
        whenever(mockUserAgentProvider.userAgent(urlString, false)).thenReturn("mobile-agent")

        assertEquals("desktop-agent", testee.userAgent(urlString, isDesktop = true))
        assertEquals("mobile-agent", testee.userAgent(urlString, isDesktop = false))
    }
}
