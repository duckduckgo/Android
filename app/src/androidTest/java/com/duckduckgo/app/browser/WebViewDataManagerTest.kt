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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebStorage.Origin
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("RemoveExplicitTypeArguments")
@SuppressLint("NoHardcodedCoroutineDispatcher")
class WebViewDataManagerTest {

    private val mockCookieManager: DuckDuckGoCookieManager = mock()
    private val mockStorage: WebStorage = mock()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockFileDeleter: FileDeleter = mock()
    private val mockWebViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val testee = WebViewDataManager(
        context,
        WebViewSessionInMemoryStorage(),
        mockCookieManager,
        mockFileDeleter,
        mockWebViewHttpAuthStore,
        feature,
    )

    @Before
    fun setup() {
        doAnswer { invocation ->
            val callback = invocation.arguments[0] as ValueCallback<Map<String, Origin>>
            callback.onReceiveValue(emptyMap()) // Simulate callback invocation
            null
        }.whenever(mockStorage).getOrigins(any())
    }

    @Test
    fun whenDataClearedThenWebViewHistoryCleared() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            assertTrue(webView.historyCleared)
        }
    }

    @Test
    fun whenDataClearedThenWebViewCacheCleared() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            assertTrue(webView.cacheCleared)
        }
    }

    @Test
    fun whenDataClearedThenWebViewFormDataCleared() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            assertTrue(webView.clearedFormData)
        }
    }

    @Test
    fun whenDataClearedThenWebViewWebStorageCleared() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            // we call deleteOrigin() instead and we should make sure we don't call deleteAllData()
            verify(mockStorage, never()).deleteAllData()
        }
    }

    @Test
    fun whenDataClearedThenWebViewAuthCredentialsCleared() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            verify(mockWebViewHttpAuthStore).clearHttpAuthUsernamePassword(webView)
        }
    }

    @Test
    fun whenDataClearedThenHttpAuthDatabaseCleaned() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            verify(mockWebViewHttpAuthStore).cleanHttpAuthDatabase()
        }
    }

    @Test
    fun whenDataClearedThenWebViewCookiesRemoved() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            verify(mockCookieManager).removeExternalCookies()
        }
    }

    private class TestWebView(context: Context) : WebView(context) {

        var historyCleared: Boolean = false
        var cacheCleared: Boolean = false
        var clearedFormData: Boolean = false

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

        override fun clearFormData() {
            super.clearFormData()
            clearedFormData = true
        }
    }
}
