/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.content.Intent
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.*
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubFrameUrlHandlerTest {

    private val testee = SubFrameUrlHandler()
    private lateinit var webView: WebView
    private val mockWebViewClientListener: WebViewClientListener = mock()

    @UiThreadTest
    @Before
    fun setUp() {
        webView = WebView(getInstrumentation().targetContext)
    }

    @Test
    fun whenWebTypeUrlThenRequestShouldNotBeOverridden() {
        assertFalse(testee.handleUrl(webView, Web(""), mockWebViewClientListener))
    }

    @Test
    fun whenTelephoneTypeUrlThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, Telephone(""), mockWebViewClientListener))
    }

    @Test
    fun whenEmailUrlTypeThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, Email(""), mockWebViewClientListener))
    }

    @Test
    fun whenSmsUrlTypeThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, Sms(""), mockWebViewClientListener))
    }

    @Test
    fun whenSearchQueryUrlTypeThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, SearchQuery(""), mockWebViewClientListener))
    }

    @Test
    fun whenIntentUrlTypeThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, IntentType("", Intent(), null), mockWebViewClientListener))
    }

    @Test
    fun whenUnknownUrlTypeThenRequestShouldBeOverridden() {
        assertTrue(testee.handleUrl(webView, Unknown(""), mockWebViewClientListener))
    }
}