/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser

import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckDuckGoWebViewTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckDuckGoWebView
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()

    @Before
    @UiThreadTest
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testee = DuckDuckGoWebView(context)
        testee.dispatcherProvider = coroutineRule.testDispatcherProvider
    }

    @Test
    @UiThreadTest
    fun whenWebViewInitialisedThenSafeBrowsingDisabled() {
        assertFalse(testee.settings.safeBrowsingEnabled)
    }

    @Test
    fun whenSafeAddDocumentStartJavaScriptWithFeatureEnabledThenAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(true)

        testee.safeAddDocumentStartJavaScript("script", setOf("*"))

        verify(mockWebViewCompatWrapper).addDocumentStartJavaScript(testee, "script", setOf("*"))
    }

    @Test
    fun whenSafeAddDocumentStartJavaScriptWithFeatureDisabledThenDoNotAddScript() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DocumentStartJavaScript)).thenReturn(false)

        testee.safeAddDocumentStartJavaScript("script", setOf("*"))

        verify(mockWebViewCompatWrapper, never()).addDocumentStartJavaScript(testee, "script", setOf("*"))
    }
}
