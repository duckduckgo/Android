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

package com.duckduckgo.app.browser.print

import android.webkit.WebView
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrintInjectorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockWebView: WebView = mock()

    private val testee = PrintInjectorJS(
        webViewCapabilityChecker = mockWebViewCapabilityChecker,
        webViewCompatWrapper = mockWebViewCompatWrapper,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenAddJsInterfaceThenPrintJavascriptInterfaceAdded() = runTest {
        testee.addJsInterface(mockWebView) {}

        verify(mockWebView).addJavascriptInterface(
            any<PrintJavascriptInterface>(),
            eq(PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME),
        )
    }

    @Test
    fun whenAddJsInterfaceAndDocumentStartScriptSupportedThenPrintOverrideRegisteredForAllFrames() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)

        testee.addJsInterface(mockWebView) {}

        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(
            eq(mockWebView),
            argThat { contains("window.print") && contains(PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME) },
            eq(setOf("*")),
        )
    }

    @Test
    fun whenAddJsInterfaceAndDocumentStartScriptNotSupportedThenNoDocumentStartScriptRegistered() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)

        testee.addJsInterface(mockWebView) {}

        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }

    @Test
    fun whenInjectPrintThenWindowPrintOverriddenInMainFrame() = runTest {
        testee.injectPrint(mockWebView)

        verify(mockWebView).loadUrl(
            argThat { startsWith("javascript:") && contains("window.print") && contains(PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME) },
        )
    }
}
