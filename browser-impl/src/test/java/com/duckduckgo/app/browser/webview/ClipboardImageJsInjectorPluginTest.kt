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

package com.duckduckgo.app.browser.webview

import android.webkit.WebView
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ClipboardImageJsInjectorPluginTest {

    private val mockClipboardImageInjector: ClipboardImageInjector = mock()
    private val mockWebView: WebView = mock()

    private lateinit var testee: ClipboardImageJsInjectorPlugin

    @Before
    fun setUp() {
        testee = ClipboardImageJsInjectorPlugin(
            clipboardImageInjector = mockClipboardImageInjector,
        )
    }

    @Test
    fun whenOnPageStartedThenInjectsPolyfillCalled() {
        testee.onPageStarted(mockWebView, "https://example.com", null, emptyList())

        verify(mockClipboardImageInjector).injectLegacyPolyfill(mockWebView)
    }

    @Test
    fun whenOnPageFinishedCalledThenDoesNothing() {
        // onPageFinished is a no-op, just verify it doesn't throw
        testee.onPageFinished(mockWebView, "https://example.com", null)

        // No interactions expected
        verify(mockClipboardImageInjector, never()).injectLegacyPolyfill(mockWebView)
    }
}
