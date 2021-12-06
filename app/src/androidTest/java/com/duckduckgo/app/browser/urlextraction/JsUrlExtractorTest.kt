/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.urlextraction

import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class JsUrlExtractorTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    lateinit var testee: DOMUrlExtractor

    @UiThreadTest
    @Before
    fun setup() {
        testee = JsUrlExtractor()
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenAddUrlExtractionThenJSInterfaceAdded() = coroutinesTestRule.runBlocking {
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        val onUrlExtracted = mock<(extractedUrl: String?) -> Unit>()
        testee.addUrlExtraction(webView, onUrlExtracted)
        verify(webView).addJavascriptInterface(any<UrlExtractionJavascriptInterface>(), eq(UrlExtractionJavascriptInterface.URL_EXTRACTION_JAVASCRIPT_INTERFACE_NAME))
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun whenPageStartedEventThenUrlExtractionJSInjected() = coroutinesTestRule.runBlocking {
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        testee.injectUrlExtractionJS(webView)
        verify(webView).evaluateJavascript(any(), anyOrNull())
    }
}
