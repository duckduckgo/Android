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
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.indexeddb.IndexedDBManager
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.browser.weblocalstorage.WebLocalStorageManager
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.appbuildconfig.api.BuildFlavor.PLAY
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File

@Suppress("RemoveExplicitTypeArguments")
@SuppressLint("NoHardcodedCoroutineDispatcher")
class WebViewDataManagerTest {

    private val mockCookieManager: DuckDuckGoCookieManager = mock()
    private val mockStorage: WebStorage = mock()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockFileDeleter: FileDeleter = mock()
    private val mockWebViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val mockWebLocalStorageManager: WebLocalStorageManager = mock()
    private val mockIndexedDBManager: IndexedDBManager = mock()
    private val mockCrashLogger: CrashLogger = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val testee = WebViewDataManager(
        context,
        WebViewSessionInMemoryStorage(),
        mockCookieManager,
        mockFileDeleter,
        mockWebViewHttpAuthStore,
        feature,
        mockWebLocalStorageManager,
        mockIndexedDBManager,
        mockCrashLogger,
        TestScope(),
        CoroutineTestRule().testDispatcherProvider,
        mockAppBuildConfig,
    )

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

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDataClearedThenWebViewWebStorageCleared() = runTest {
        withContext(Dispatchers.Main) {
            feature.webLocalStorage().setRawStoredState(State(enable = true))
            val webView = TestWebView(context)
            testee.clearData(webView, mockStorage)
            verify(mockWebLocalStorageManager).clearWebLocalStorage()
            verify(mockStorage, never()).deleteAllData()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDataClearedAndThrowsExceptionAndInternalThenSendCrashPixelAndDeleteAllData() = runTest {
        withContext(Dispatchers.Main) {
            feature.webLocalStorage().setRawStoredState(State(enable = true))
            val exception = RuntimeException("test")
            val webView = TestWebView(context)
            whenever(mockAppBuildConfig.flavor).thenReturn(INTERNAL)
            whenever(mockWebLocalStorageManager.clearWebLocalStorage()).thenThrow(exception)
            testee.clearData(webView, mockStorage)
            verify(mockWebLocalStorageManager).clearWebLocalStorage()
            verify(mockCrashLogger).logCrash(CrashLogger.Crash(shortName = "web_storage_on_clear_error", t = exception))
            verify(mockStorage).deleteAllData()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDataClearedAndThrowsExceptionAndNotInternalThenDoNotSendCrashPixelAndDeleteAllData() = runTest {
        withContext(Dispatchers.Main) {
            feature.webLocalStorage().setRawStoredState(State(enable = true))
            val exception = RuntimeException("test")
            val webView = TestWebView(context)
            whenever(mockAppBuildConfig.flavor).thenReturn(PLAY)
            whenever(mockWebLocalStorageManager.clearWebLocalStorage()).thenThrow(exception)
            testee.clearData(webView, mockStorage)
            verify(mockWebLocalStorageManager).clearWebLocalStorage()
            verifyNoInteractions(mockCrashLogger)
            verify(mockStorage).deleteAllData()
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

    @Test
    fun whenClearDataThenAppWebviewContentsDeletedExceptDefaultAndCookies() = runTest {
        withContext(Dispatchers.Main) {
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview"),
                listOf("Default", "Cookies"),
            )
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenClearDataAndWebLocalStorageFeatureDisabledThenDefaultContentsDeletedExceptCookies() = runTest {
        withContext(Dispatchers.Main) {
            feature.webLocalStorage().setRawStoredState(State(enable = false))
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview/Default"),
                listOf("Cookies"),
            )
            verifyNoInteractions(mockWebLocalStorageManager)
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenClearDataAndWebLocalStorageFeatureEnabledThenDefaultContentsDeletedExceptCookiesAndLocalStorage() = runTest {
        withContext(Dispatchers.Main) {
            feature.webLocalStorage().setRawStoredState(State(enable = true))
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview/Default"),
                listOf("Cookies", "Local Storage"),
            )
            verify(mockWebLocalStorageManager).clearWebLocalStorage()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenClearDataAndIndexedDBFeatureDisabledThenDefaultContentsDeletedExceptCookies() = runTest {
        withContext(Dispatchers.Main) {
            feature.indexedDB().setRawStoredState(State(enable = false))
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview/Default"),
                listOf("Cookies"),
            )
            verifyNoInteractions(mockIndexedDBManager)
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenClearDataAndIndexedDBFeatureEnabledThenDefaultContentsDeletedExceptCookiesAndIndexedDB() = runTest {
        withContext(Dispatchers.Main) {
            feature.indexedDB().setRawStoredState(State(enable = true))
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview/Default"),
                listOf("Cookies", "IndexedDB"),
            )
            verify(mockIndexedDBManager).clearIndexedDB()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenClearDataAndIndexedDBThrowsExceptionThenDefaultContentsDeletedExceptCookies() = runTest {
        whenever(mockIndexedDBManager.clearIndexedDB()).thenThrow(RuntimeException())
        withContext(Dispatchers.Main) {
            feature.indexedDB().setRawStoredState(State(enable = true))
            val webView = TestWebView(context)

            testee.clearData(webView, mockStorage)

            verify(mockFileDeleter).deleteContents(
                File(context.applicationInfo.dataDir, "app_webview/Default"),
                listOf("Cookies"),
            )
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
