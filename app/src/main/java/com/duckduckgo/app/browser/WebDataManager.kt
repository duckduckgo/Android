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
import android.webkit.WebStorage.Origin
import android.webkit.WebView
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface WebDataManager {
    suspend fun clearData(
        webView: WebView,
        webStorage: WebStorage,
    )

    fun clearWebViewSessions()
}

class WebViewDataManager @Inject constructor(
    private val context: Context,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val cookieManager: DuckDuckGoCookieManager,
    private val fileDeleter: FileDeleter,
    private val webViewHttpAuthStore: WebViewHttpAuthStore,
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
            webStorage.getOrigins { origins ->
                kotlin.runCatching {
                    for (origin in origins.values) {
                        val originString = (origin as Origin).origin

                        // Check if this is the domain to exclude
                        if (!originString.endsWith(".duckduckgo.com")) {
                            // Delete all other origins
                            webStorage.deleteOrigin(originString)
                        }
                    }
                    continuation.resume(Unit)
                }.onFailure {
                    // fallback, if we crash we delete everything
                    webStorage.deleteAllData()
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun clearFormData(webView: WebView) {
        webView.clearFormData()
    }

    /**
     * Deletes web view directory content except the following directories
     *  app_webview/Cookies
     *  app_webview/Default/Cookies
     *  app_webview/Default/Local Storage
     *
     *  the excluded directories above are to avoid clearing unnecessary cookies and because localStorage is cleared using clearWebStorage
     */
    private suspend fun clearWebViewDirectories() {
        val dataDir = context.applicationInfo.dataDir
        fileDeleter.deleteContents(File(dataDir, "app_webview"), listOf("Default", "Cookies"))

        // We don't delete the Default dir as Cookies may be inside however we do clear any other content
        fileDeleter.deleteContents(File(dataDir, "app_webview/Default"), listOf("Cookies", "Local Storage"))
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
