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

package com.duckduckgo.eventhub.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import com.duckduckgo.eventhub.impl.pixels.EventHubPixelManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SuppressLint("DenyListedApi")
class EventHubJsInjectorPluginTest {

    private val pixelManager: EventHubPixelManager = mock()
    private val webView: WebView = mock()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)

    private val plugin = EventHubJsInjectorPlugin(pixelManager = pixelManager, eventHubFeature = eventHubFeature)

    @Test
    fun `onPageStarted calls onNavigationStarted when feature enabled`() {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = true))
        val url = "https://example.com"
        plugin.onPageStarted(webView, url, null, emptyList())

        val webViewIdCaptor = argumentCaptor<String>()
        val urlCaptor = argumentCaptor<String>()
        verify(pixelManager).onNavigationStarted(webViewIdCaptor.capture(), urlCaptor.capture())
        assertEquals(System.identityHashCode(webView).toString(), webViewIdCaptor.firstValue)
        assertEquals(url, urlCaptor.firstValue)
    }

    @Test
    fun `onPageStarted passes empty string when url is null`() {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = true))
        plugin.onPageStarted(webView, null, null, emptyList())

        val urlCaptor = argumentCaptor<String>()
        verify(pixelManager).onNavigationStarted(any(), urlCaptor.capture())
        assertEquals("", urlCaptor.firstValue)
    }

    @Test
    fun `onPageStarted does nothing when feature disabled`() {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = false))
        plugin.onPageStarted(webView, "https://example.com", null, emptyList())

        verify(pixelManager, never()).onNavigationStarted(any(), any())
    }
}
