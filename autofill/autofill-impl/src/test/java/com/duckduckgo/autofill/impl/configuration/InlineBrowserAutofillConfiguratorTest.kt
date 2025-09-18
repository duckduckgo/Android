/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration

import android.webkit.WebView
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InlineBrowserAutofillConfiguratorTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var inlineBrowserAutofillConfigurator: InlineBrowserAutofillConfigurator

    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider = mock()
    private val webView: WebView = mock()
    private val autofillCapabilityChecker: AutofillCapabilityChecker = mock()
    private val autofillJavascriptLoader: AutofillJavascriptLoader = mock()

    @Before
    fun before() = runTest {
        whenever(autofillJavascriptLoader.getAutofillJavascript()).thenReturn("")
        whenever(autofillRuntimeConfigProvider.getRuntimeConfiguration(any(), any(), any())).thenReturn("")

        val internalConfigurator = RealInlineBrowserAutofillConfigurator(
            autofillRuntimeConfigProvider,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            autofillCapabilityChecker,
            autofillJavascriptLoader,
        )
        inlineBrowserAutofillConfigurator = InlineBrowserAutofillConfigurator(internalConfigurator)
    }

    @Test
    fun whenFeatureIsNotEnabledThenDoNotInject() = runTest {
        givenFeatureIsDisabled()
        inlineBrowserAutofillConfigurator.configureAutofillForCurrentPage(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript("javascript:", null)
    }

    @Test
    fun whenFeatureIsEnabledThenInject() = runTest {
        givenFeatureIsEnabled()
        inlineBrowserAutofillConfigurator.configureAutofillForCurrentPage(webView, "https://example.com")

        verify(webView).evaluateJavascript("javascript:", null)
    }

    private suspend fun givenFeatureIsEnabled() {
        whenever(autofillCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(true)
    }

    private suspend fun givenFeatureIsDisabled() {
        whenever(autofillCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(false)
    }
}
