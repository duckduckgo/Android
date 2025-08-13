/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import android.webkit.WebView
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatWebViewClientTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Test
    fun whenOnPageStartedCalledThenJsPluginOnPageStartedInvoked() = runTest {
        val mockPlugin: JsInjectorPlugin = mock()
        val pluginPoint: PluginPoint<JsInjectorPlugin> = mock()
        whenever(pluginPoint.getPlugins()).thenReturn(listOf(mockPlugin))

        val duckChatWebViewClient = DuckChatWebViewClient(pluginPoint, coroutineRule.testScope)
        val webView: WebView = mock()
        val url = "https://example.com"

        duckChatWebViewClient.onPageStarted(webView, url, null)

        verify(mockPlugin).onPageStarted(webView, url, null)
    }
}
