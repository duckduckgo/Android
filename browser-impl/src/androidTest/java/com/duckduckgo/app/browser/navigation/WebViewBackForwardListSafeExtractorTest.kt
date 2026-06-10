/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.navigation

import android.content.Context
import android.webkit.WebBackForwardList
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewBackForwardListSafeExtractorTest {

    private val context = getInstrumentation().targetContext

    @UiThreadTest
    @Test
    fun whenCopyBackForwardListCalledAndExceptionThrownThenNavigationListIsNull() {
        val testWebView = TestWebViewThrowsNullPointerException(context)
        assertNull(testWebView.safeCopyBackForwardList())
    }

    @UiThreadTest
    @Test
    fun whenCopyBackForwardListCalledAndNoExceptionThrownThenNavigationListIsNotNull() {
        val testWebView = WebView(context)
        assertNotNull(testWebView.safeCopyBackForwardList())
    }
}

class TestWebViewThrowsNullPointerException(context: Context) : WebView(context) {

    override fun copyBackForwardList(): WebBackForwardList {
        throw NullPointerException("Deliberate")
    }
}
