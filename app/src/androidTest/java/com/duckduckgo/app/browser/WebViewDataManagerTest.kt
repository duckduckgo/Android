/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("RemoveExplicitTypeArguments")
class WebViewDataManagerTest {

    private val mockCookieManager: DuckDuckGoCookieManager = mock()
    private val mockStorage: WebStorage = mock()
    private val testee = WebViewDataManager(WebViewSessionInMemoryStorage(), mockCookieManager)

    @UiThreadTest
    @Test
    fun whenDataClearedThenCacheHistoryAndStorageDataCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val webView = TestWebView(context)
        testee.clearData(webView, mockStorage, context)
        assertTrue(webView.historyCleared)
        assertTrue(webView.cacheCleared)
        verify(mockStorage).deleteAllData()
    }

    @Test
    fun whenExternalCookiesClearedThenCookiesRemoved() = runBlocking<Unit> {
        testee.clearExternalCookies()
        verify(mockCookieManager).removeExternalCookies()
    }

    private class TestWebView(context: Context) : WebView(context) {

        var historyCleared: Boolean = false
        var cacheCleared: Boolean = false

        override fun clearHistory() {
            super.clearHistory()

            historyCleared = true
        }

        override fun clearCache(includeDiskFiles: Boolean) {
            super.clearCache(includeDiskFiles)

            if (includeDiskFiles) {
                cacheCleared = true
            }
        }
    }
}