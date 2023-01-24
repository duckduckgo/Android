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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeatureName
import com.duckduckgo.feature.toggles.api.FeatureToggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@ExperimentalCoroutinesApi
class InlineBrowserAutofillConfiguratorTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var inlineBrowserAutofillConfigurator: InlineBrowserAutofillConfigurator

    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider = mock()
    private val javascriptInjector: JavascriptInjector = mock()
    private val autofill: Autofill = mock()
    private val featureToggle: FeatureToggle = mock()
    private val webView: WebView = mock()

    @Before
    fun before() = runTest {
        whenever(javascriptInjector.getFunctionsJS()).thenReturn("")
        whenever(autofillRuntimeConfigProvider.getRuntimeConfiguration(any(), any())).thenReturn("")

        inlineBrowserAutofillConfigurator = InlineBrowserAutofillConfigurator(
            autofillRuntimeConfigProvider,
            javascriptInjector,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            autofill,
            featureToggle,
        )
    }

    @Test
    fun whenFeatureIsNotEnabledThenDoNotInject() {
        givenFeatureIsDisabled()
        inlineBrowserAutofillConfigurator.configureAutofillForCurrentPage(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript("javascript:", null)
    }

    @Test
    fun whenFeatureIsEnabledAndUrlIsExceptionThenDoNotInject() {
        givenUrlIsAnException()
        givenFeatureIsEnabled()
        inlineBrowserAutofillConfigurator.configureAutofillForCurrentPage(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript("javascript:", null)
    }

    @Test
    fun whenFeatureIsEnabledAndUrlIsNotExceptionThenInject() {
        givenUrlIsNotAnException()
        givenFeatureIsEnabled()
        inlineBrowserAutofillConfigurator.configureAutofillForCurrentPage(webView, "https://example.com")

        verify(webView).evaluateJavascript("javascript:", null)
    }

    private fun givenUrlIsAnException() {
        whenever(autofill.isAnException(any())).thenReturn(true)
    }

    private fun givenUrlIsNotAnException() {
        whenever(autofill.isAnException(any())).thenReturn(false)
    }

    private fun givenFeatureIsEnabled() {
        whenever(featureToggle.isFeatureEnabled(AutofillFeatureName.Autofill.value, true)).thenReturn(true)
    }

    private fun givenFeatureIsDisabled() {
        whenever(featureToggle.isFeatureEnabled(AutofillFeatureName.Autofill.value, true)).thenReturn(false)
    }
}
