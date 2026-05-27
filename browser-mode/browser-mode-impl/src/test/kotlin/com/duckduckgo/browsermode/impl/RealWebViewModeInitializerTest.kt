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

package com.duckduckgo.browsermode.impl

import android.webkit.WebView
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.api.profileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class RealWebViewModeInitializerTest {

    private val webView: WebView = mock()
    private val fireModeAvailability = FakeFireModeAvailability()
    private val webViewProfileBinder = FakeWebViewProfileBinder()
    private val testee = RealWebViewModeInitializer(
        fireModeAvailability = fireModeAvailability,
        webViewProfileBinder = webViewProfileBinder,
    )

    @Test
    fun `bind returns success when Fire mode is available and Fire profile binding succeeds`() {
        val result = testee.bind(webView, BrowserMode.FIRE)

        assertTrue(result.isSuccess)
        assertEquals(listOf(BoundProfile(webView, BrowserMode.FIRE.profileName)), webViewProfileBinder.boundProfiles)
    }

    @Test
    fun `bind returns success when Fire mode is available and regular profile binding succeeds`() {
        val result = testee.bind(webView, BrowserMode.REGULAR)

        assertTrue(result.isSuccess)
        assertEquals(listOf(BoundProfile(webView, BrowserMode.REGULAR.profileName)), webViewProfileBinder.boundProfiles)
    }

    @Test
    fun `bind returns failure when profile binding throws`() {
        val expected = IllegalStateException("WebKit profile binding failed")
        webViewProfileBinder.exception = expected

        val result = testee.bind(webView, BrowserMode.FIRE)

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `bind returns failure and skips profile binding when Fire mode is unavailable for Fire profile`() {
        fireModeAvailability.available = false

        val result = testee.bind(webView, BrowserMode.FIRE)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(webViewProfileBinder.boundProfiles.isEmpty())
    }

    @Test
    fun `bind returns failure and skips profile binding when Fire mode is unavailable for regular profile`() {
        fireModeAvailability.available = false

        val result = testee.bind(webView, BrowserMode.REGULAR)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(webViewProfileBinder.boundProfiles.isEmpty())
    }

    private class FakeFireModeAvailability : FireModeAvailability {
        var available = true

        override fun isAvailable(): Boolean = available
    }

    private class FakeWebViewProfileBinder : WebViewProfileBinder {
        val boundProfiles = mutableListOf<BoundProfile>()
        var exception: Throwable? = null

        override fun bind(webView: WebView, profileName: String) {
            exception?.let { throw it }
            boundProfiles.add(BoundProfile(webView, profileName))
        }
    }

    private data class BoundProfile(
        val webView: WebView,
        val profileName: String,
    )
}
