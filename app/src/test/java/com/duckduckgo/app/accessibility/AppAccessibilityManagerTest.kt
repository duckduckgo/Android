/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.accessibility

import android.webkit.WebView
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class AppAccessibilityManagerTest {

    private val accessibilitySettingsDataStore = mock<AccessibilitySettingsDataStore>()

    private val webView = mock<WebView>()

    private val appAccessibilityManager = AppAccessibilityManager(accessibilitySettingsDataStore)

    @Test
    fun whenForceZoomEnabledThenJavaScriptInjected() {
        forceZoom(enabled = true)

        appAccessibilityManager.onPageFinished(webView, "http://example.com")

        verify(webView).loadUrl(any())
    }

    @Test
    fun whenForceZoomDisabledThenNoInteractions() {
        forceZoom(enabled = false)

        appAccessibilityManager.onPageFinished(webView, "http://example.com")

        verifyNoInteractions(webView)
    }

    private fun forceZoom(enabled: Boolean) {
        whenever(accessibilitySettingsDataStore.forceZoom).thenReturn(enabled)
    }
}
