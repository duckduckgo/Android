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

package com.duckduckgo.app.email

import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Autofill
import com.duckduckgo.privacy.config.api.PrivacyFeatureName.AutofillFeatureName
import org.mockito.kotlin.*
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader

class EmailInjectorJsTest {

    private val mockEmailManager: EmailManager = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockAutofill: Autofill = mock()
    lateinit var testee: EmailInjectorJs

    @Before
    fun setup() {
        testee = EmailInjectorJs(mockEmailManager, DuckDuckGoUrlDetector(), mockDispatcherProvider, mockFeatureToggle, mockAutofill)

        whenever(mockFeatureToggle.isFeatureEnabled(AutofillFeatureName)).thenReturn(true)
        whenever(mockAutofill.isAnException(any())).thenReturn(false)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoDomainThenInjectJsCode() {
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://duckduckgo.com/email")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoSubdomainThenDoNotInjectJsCode() {
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://test.duckduckgo.com/email")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsNotFromDuckDuckGoAndEmailIsSignedInThenInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsNotFromDuckDuckGoAndEmailIsNotSignedInThenDoNotInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoAndFeatureIsDisabledThenInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        whenever(mockFeatureToggle.isFeatureEnabled(AutofillFeatureName)).thenReturn(false)

        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://duckduckgo.com/email")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoAndUrlIsInExceptionsThenInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        whenever(mockAutofill.isAnException(any())).thenReturn(true)

        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://duckduckgo.com/email")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectAddressThenInjectJsCodeReplacingTheAlias() {
        val address = "address"
        val jsToEvaluate = getAliasJsToEvaluate().replace("%s", address)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectAddressInEmailField(webView, address, "https://example.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectAddressAndFeatureIsDisabledThenJsCodeNotInjected() {
        whenever(mockFeatureToggle.isFeatureEnabled(AutofillFeatureName)).thenReturn(false)

        val address = "address"
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectAddressInEmailField(webView, address, "https://example.com")

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectAddressAndUrlIsAnExceptionThenJsCodeNotInjected() {
        whenever(mockAutofill.isAnException(any())).thenReturn(true)

        val address = "address"
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectAddressInEmailField(webView, address, "https://example.com")

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    private fun getJsToEvaluate(): String {
        val js = readResource().use { it?.readText() }.orEmpty()
        return "javascript:$js"
    }

    private fun getAliasJsToEvaluate(): String {
        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.inject_alias)
            .bufferedReader()
            .use { it.readText() }
        return "javascript:$js"
    }

    private fun readResource(): BufferedReader? {
        return javaClass.classLoader?.getResource("autofill.js")?.openStream()?.bufferedReader()
    }
}
