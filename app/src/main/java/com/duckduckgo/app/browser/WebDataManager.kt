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
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.indexeddb.IndexedDBManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.weblocalstorage.WebLocalStorageManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

interface WebDataManager {
    suspend fun clearData(
        webView: WebView,
        webStorage: WebStorage,
    )

    fun clearWebViewSessions()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class WebViewDataManager @Inject constructor(
    private val context: Context,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val cookieManager: DuckDuckGoCookieManager,
    private val fileDeleter: FileDeleter,
    private val webViewHttpAuthStore: WebViewHttpAuthStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val webLocalStorageManager: WebLocalStorageManager,
    private val indexedDBManager: IndexedDBManager,
    private val crashLogger: CrashLogger,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : WebDataManager {

    override suspend fun clearData(
        webView: WebView,
        webStorage: WebStorage,
    ) {
        clearWebViewCache(webView)
        clearHistory(webView)
        clearWebStorage(webStorage)
        clearFormData(webView)
        clearAuthentication(webView)
        clearExternalCookies()
        clearWebViewDirectories()
    }

    private fun clearWebViewCache(webView: WebView) {
        webView.clearCache(true)
    }

    private fun clearHistory(webView: WebView) {
        webView.clearHistory()
    }

    private suspend fun clearWebStorage(webStorage: WebStorage) {
        suspendCoroutine { continuation ->
            if (androidBrowserConfigFeature.webLocalStorage().isEnabled()) {
                kotlin.runCatching {
                    webLocalStorageManager.clearWebLocalStorage()
                    continuation.resume(Unit)
                }.onFailure { e ->
                    logcat(ERROR) { "WebDataManager: Could not selectively clear web storage: ${e.asLog()}" }
                    if (appBuildConfig.isInternalBuild()) {
                        sendCrashPixel(e)
                    }
                    // fallback, if we crash we delete everything
                    webStorage.deleteAllData()
                    continuation.resume(Unit)
                }
            } else {
                webStorage.deleteAllData()
                continuation.resume(Unit)
            }
        }
    }

    private fun sendCrashPixel(e: Throwable) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            crashLogger.logCrash(CrashLogger.Crash(shortName = "web_storage_on_clear_error", t = e))
        }
    }

    private fun clearFormData(webView: WebView) {
        webView.clearFormData()
    }

    /**
     * Deletes web view directory content except the following directories
     *  app_webview/Cookies
     *  app_webview/Default/Cookies
     *  app_webview/Default/Local Storage (when flag enabled)
     *  app_webview/Default/IndexedDB (when flag enabled)
     *
     *  the excluded directories above are to avoid clearing unnecessary cookies and because localStorage is cleared using clearWebStorage
     */
    private suspend fun clearWebViewDirectories() {
        val dataDir = context.applicationInfo.dataDir
        fileDeleter.deleteContents(File(dataDir, "app_webview"), listOf("Default", "Cookies"))

        // We don't delete the Default dir as Cookies may be inside however we do clear any other content
        val excludedDirectories = mutableListOf("Cookies")

        if (androidBrowserConfigFeature.webLocalStorage().isEnabled()) {
            excludedDirectories.add("Local Storage")
        }
        if (androidBrowserConfigFeature.indexedDB().isEnabled()) {
            runCatching {
                indexedDBManager.clearIndexedDB()
            }.onSuccess {
                excludedDirectories.add("IndexedDB")
            }.onFailure { t ->
                logcat(WARN) { "Failed to clear IndexedDB, will delete it instead: ${t.asLog()}" }
            }
        }
        fileDeleter.deleteContents(File(dataDir, "app_webview/Default"), excludedDirectories)
    }

    private suspend fun clearAuthentication(webView: WebView) {
        webViewHttpAuthStore.clearHttpAuthUsernamePassword(webView)
        webViewHttpAuthStore.cleanHttpAuthDatabase()
    }

    private suspend fun clearExternalCookies() {
        cookieManager.removeExternalCookies()
    }

    override fun clearWebViewSessions() {
        webViewSessionStorage.deleteAllSessions()
    }
}
