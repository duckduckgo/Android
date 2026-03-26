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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.webkit.WebView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream

@SuppressLint("DenyListedApi")
class ClipboardImageInjectorImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockContext: Context = mock()
    private val mockResources: Resources = mock()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockWebView: WebView = mock()

    private lateinit var fakeFeatureToggle: WebViewClipboardImageFeature
    private lateinit var testee: ClipboardImageInjector

    @Before
    fun setUp() = runTest {
        fakeFeatureToggle = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "webViewClipboardImage",
        ).build().create(WebViewClipboardImageFeature::class.java)

        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.openRawResource(R.raw.clipboard_polyfill_legacy))
            .thenAnswer { ByteArrayInputStream("legacy polyfill script".toByteArray()) }
        whenever(mockResources.openRawResource(R.raw.clipboard_polyfill))
            .thenAnswer { ByteArrayInputStream("modern polyfill script".toByteArray()) }
        whenever(mockWebViewCapabilityChecker.isSupported(any())).thenReturn(false)

        testee = ClipboardImageInjectorImpl(
            webViewClipboardImageFeature = fakeFeatureToggle,
            webViewCapabilityChecker = mockWebViewCapabilityChecker,
            webViewCompatWrapper = mockWebViewCompatWrapper,
            appBuildConfig = mockAppBuildConfig,
            context = mockContext,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )

        // Wait for init coroutine to complete
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
    }

    @Test
    fun whenLegacyApproachConfiguredThenJavascriptInterfaceAdded() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = true))
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(false)
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(false)

        testee.configureWebViewForClipboard(mockWebView)

        verify(mockWebView).addJavascriptInterface(
            any<ClipboardImageJavascriptInterface>(),
            eq(ClipboardImageJavascriptInterface.JAVASCRIPT_INTERFACE_NAME),
        )
    }

    @Test
    fun whenFeatureDisabledThenJavascriptInterfaceNotAdded() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = false))

        testee.configureWebViewForClipboard(mockWebView)

        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun whenInjectLegacyPolyfillCalledAndScriptLoadedThenEvaluatesJavascript() = runTest {
        testee.injectLegacyPolyfill(mockWebView)

        verify(mockWebView).evaluateJavascript(argThat { startsWith("javascript:") }, isNull())
    }

    @Test
    fun whenModernApproachSupportedThenUsesModernApproach() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = true))
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(true)

        testee.configureWebViewForClipboard(mockWebView)

        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(eq(mockWebView), any(), any())
        verify(mockWebViewCompatWrapper).addWebMessageListener(eq(mockWebView), eq("ddgClipboardObj"), any(), any())
        verify(mockWebView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun whenOnlyWebMessageListenerSupportedThenUsesLegacyApproach() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = true))
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(false)

        testee.configureWebViewForClipboard(mockWebView)

        verify(mockWebView).addJavascriptInterface(
            any<ClipboardImageJavascriptInterface>(),
            eq(ClipboardImageJavascriptInterface.JAVASCRIPT_INTERFACE_NAME),
        )
        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(any(), any(), any())
    }

    @Test
    fun whenOnlyDocumentStartJavaScriptSupportedThenUsesLegacyApproach() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = true))
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(false)
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(true)

        testee.configureWebViewForClipboard(mockWebView)

        verify(mockWebView).addJavascriptInterface(
            any<ClipboardImageJavascriptInterface>(),
            eq(ClipboardImageJavascriptInterface.JAVASCRIPT_INTERFACE_NAME),
        )
        verify(mockWebViewCompatWrapper, never()).addWebMessageListener(any(), any(), any(), any())
    }

    @Test
    fun whenModernApproachConfiguredThenInjectLegacyPolyfillDoesNothing() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = true))
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)
        whenever(mockWebViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(true)

        testee.configureWebViewForClipboard(mockWebView)
        testee.injectLegacyPolyfill(mockWebView)

        verify(mockWebView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun whenFeatureDisabledThenInjectLegacyPolyfillDoesNothing() = runTest {
        fakeFeatureToggle.self().setRawStoredState(State(enable = false))

        testee.configureWebViewForClipboard(mockWebView)
        testee.injectLegacyPolyfill(mockWebView)

        verify(mockWebView, never()).evaluateJavascript(any(), any())
    }
}
