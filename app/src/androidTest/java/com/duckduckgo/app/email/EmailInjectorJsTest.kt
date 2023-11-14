/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.app.autofill.FileBasedJavascriptInjector
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.R
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.io.BufferedReader
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class EmailInjectorJsTest {

    private val mockEmailManager: EmailManager = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockAutofillFeature: AutofillFeature = mock()
    private val mockAutofill: Autofill = mock()
    private val javascriptInjector: JavascriptInjector = FileBasedJavascriptInjector()

    lateinit var testee: EmailInjectorJs

    @Before
    fun setup() {
        testee =
            EmailInjectorJs(
                mockEmailManager,
                DuckDuckGoUrlDetectorImpl(),
                mockDispatcherProvider,
                mockAutofillFeature,
                javascriptInjector,
                mockAutofill,
            )

        whenever(mockAutofillFeature.self()).thenReturn(
            object : Toggle {
                var state: Toggle.State? = null

                override fun isEnabled(): Boolean = state?.enable ?: false

                override fun setEnabled(state: Toggle.State) {
                    this.state = state
                }

                override fun getRawStoredState(): State? = this.state
            },
        )
        whenever(mockAutofill.isAnException(any())).thenReturn(false)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectAddressThenInjectJsCodeReplacingTheAlias() {
        val address = "address"
        val jsToEvaluate = getAliasJsToEvaluate().replace("%s", address)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        mockAutofillFeature.self().setEnabled(Toggle.State(enable = true))

        testee.injectAddressInEmailField(webView, address, "https://example.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenInjectAddressAndFeatureIsDisabledThenJsCodeNotInjected() {
        mockAutofillFeature.self().setEnabled(Toggle.State(enable = true))

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

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenNotifyWebAppSignEventAndUrlIsNotFromDuckDuckGoAndEmailIsSignedInThenDoNotEvaluateJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        val jsToEvaluate = getNotifySignOutJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.notifyWebAppSignEvent(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenNotifyWebAppSignEventAndUrlIsNotFromDuckDuckGoAndEmailIsNotSignedInThenDoNotEvaluateJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        val jsToEvaluate = getNotifySignOutJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.notifyWebAppSignEvent(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenNotifyWebAppSignEventAndUrlIsFromDuckDuckGoAndFeatureIsDisabledAndEmailIsNotSignedInThenDoNotEvaluateJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        mockAutofillFeature.self().setEnabled(Toggle.State(enable = false))

        val jsToEvaluate = getNotifySignOutJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.notifyWebAppSignEvent(webView, "https://duckduckgo.com/email")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenNotifyWebAppSignEventAndUrlIsFromDuckDuckGoAndFeatureIsEnabledAndEmailIsNotSignedInThenEvaluateJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        mockAutofillFeature.self().setEnabled(Toggle.State(enable = true))

        val jsToEvaluate = getNotifySignOutJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.notifyWebAppSignEvent(webView, "https://duckduckgo.com/email")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    private fun getAliasJsToEvaluate(): String {
        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.inject_alias)
            .bufferedReader()
            .use { it.readText() }
        return "javascript:$js"
    }

    private fun getNotifySignOutJsToEvaluate(): String {
        val js =
            InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.signout_autofill)
                .bufferedReader()
                .use { it.readText() }
        return "javascript:$js"
    }

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}
