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

package com.duckduckgo.app.browser.suggestredirect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SuggestRedirectEvaluatorTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val hostnameResolver: HostnameResolver = mock()

    private val testee: SuggestRedirectEvaluator = RealSuggestRedirectEvaluator(hostnameResolver, coroutineRule.testDispatcherProvider)

    @Test
    fun `when URL has no host, then returns null and performs no lookups`() = runTest {
        listOf(
            "",
            "   ",
            "not a url",
            "example.com", // Missing scheme in the URL
        ).forEach { url ->
            assertNull("Expected suggestRedirect(\"$url\") to be null", testee.suggestRedirect(url))
        }
        verifyNoInteractions(hostnameResolver)
    }

    @Test
    fun `when host already has a www prefix, then returns null and performs no lookups`() = runTest {
        assertNull(testee.suggestRedirect("https://www.example.com"))
        verifyNoInteractions(hostnameResolver)
    }

    @Test
    fun `when host is not a registrable domain, then returns null and performs no lookups`() = runTest {
        listOf(
            "https://localhost",
            "https://a.b.example.com",
            "https://.com",
            "https://com",
            "https://127.0.0.1",
            "https://62.62.62.62",
            "https://::1",
            "https://2001:db8::1",
            "https://fe80::1%eth0",
            "https://[2001:db8::1]",
        ).forEach { url ->
            assertNull("Expected suggestRedirect(\"$url\") to be null", testee.suggestRedirect(url))
        }
        verifyNoInteractions(hostnameResolver)
    }

    @Test
    fun `when www variant of the host resolves, then returns the suggestion`() = runTest {
        whenever(hostnameResolver.resolves("www.example.com")).thenReturn(true)

        assertEquals(
            RedirectSuggestion(domain = "www.example.com", url = "https://www.example.com"),
            testee.suggestRedirect("https://example.com"),
        )
    }

    @Test
    fun `when www variant of the host does not resolve, then returns null`() = runTest {
        whenever(hostnameResolver.resolves("www.example.com")).thenReturn(false)

        assertNull(testee.suggestRedirect("https://example.com"))
    }

    @Test
    fun `when redirect is suggested, then the suggested URL keeps the original scheme, path and query`() = runTest {
        whenever(hostnameResolver.resolves("www.example.com")).thenReturn(true)

        assertEquals(
            RedirectSuggestion(domain = "www.example.com", url = "http://www.example.com/path?q=1"),
            testee.suggestRedirect("http://example.com/path?q=1"),
        )
    }

    @Test
    fun `when redirect is suggested, then the suggested URL keeps the original port`() = runTest {
        whenever(hostnameResolver.resolves("www.example.com")).thenReturn(true)

        assertEquals(
            RedirectSuggestion(domain = "www.example.com", url = "http://www.example.com:8080/path"),
            testee.suggestRedirect("http://example.com:8080/path"),
        )
    }
}
