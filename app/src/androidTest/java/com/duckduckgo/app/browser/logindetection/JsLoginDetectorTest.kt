/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.logindetection

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.logindetection.LoginDetectionJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class JsLoginDetectorTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val settingsDataStore: SettingsDataStore = mock()
    private val testee = JsLoginDetector(settingsDataStore)

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenAddLoginDetectionThenJSInterfaceAdded() = coroutinesTestRule.runBlocking {
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        testee.addLoginDetection(webView) {}
        verify(webView).addJavascriptInterface(any<LoginDetectionJavascriptInterface>(), eq(JAVASCRIPT_INTERFACE_NAME))
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionDisabledAndPageStartedEventThenNoWebViewInteractions() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(false)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        testee.onEvent(WebNavigationEvent.OnPageStarted(webView))
        verifyZeroInteractions(webView)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionDisabledAndInterceptRequestEventThenNoWebViewInteractions() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(false)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        val webResourceRequest = aWebResourceRequest()
        testee.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
        verifyZeroInteractions(webView)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionEnabledAndPageStartedEventThenJSLoginDetectionInjected() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(true)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        testee.onEvent(WebNavigationEvent.OnPageStarted(webView))
        verify(webView).evaluateJavascript(any(), anyOrNull())
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionEnabledAndLoginPostRequestCapturedThenJSLoginDetectionInjected() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(true)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        val webResourceRequest = aWebResourceRequest("POST", "login")
        testee.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
        verify(webView).evaluateJavascript(any(), anyOrNull())
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionEnabledAndNoLoginPostRequestCapturedThenNoWebViewInteractions() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(true)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        val webResourceRequest = aWebResourceRequest("POST", "")
        testee.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
        verifyZeroInteractions(webView)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenLoginDetectionEnabledAndGetRequestCapturedThenNoWebViewInteractions() = coroutinesTestRule.runBlocking {
        whenever(settingsDataStore.appLoginDetection).thenReturn(true)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        val webResourceRequest = aWebResourceRequest("GET")
        testee.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
        verifyZeroInteractions(webView)
    }

    private fun aWebResourceRequest(
        httpMethod: String = "POST",
        path: String = "login"
    ): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("https://example.com/$path")

            override fun isRedirect(): Boolean = false

            override fun getMethod(): String = httpMethod

            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()

            override fun hasGesture(): Boolean = false

            override fun isForMainFrame(): Boolean = false
        }
    }
}
