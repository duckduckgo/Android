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
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.httpauth.db.clearFormDataCompat
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.global.file.FileDeleter
import java.io.File
import javax.inject.Inject

interface WebDataManager {
    suspend fun clearData(webView: WebView, webStorage: WebStorage)
    fun clearWebViewSessions()
}

class WebViewDataManager @Inject constructor(
    private val context: Context,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val cookieManager: DuckDuckGoCookieManager,
    private val fileDeleter: FileDeleter,
    private val webViewHttpAuthStore: WebViewHttpAuthStore
) : WebDataManager {

    override suspend fun clearData(webView: WebView, webStorage: WebStorage) {
        clearWebViewCache(webView)
        clearHistory(webView)
        clearWebStorage(webStorage)
        clearFormData(webView)
        clearAuthentication(webView)
        clearExternalCookies()
        clearWebViewDirectories(exclusions = WEBVIEW_FILES_EXCLUDED_FROM_DELETION)
    }

    private fun clearWebViewCache(webView: WebView) {
        webView.clearCache(true)
    }

    private fun clearHistory(webView: WebView) {
        webView.clearHistory()
    }

    private fun clearWebStorage(webStorage: WebStorage) {
        webStorage.deleteAllData()
    }

    private fun clearFormData(webView: WebView) {
        webView.clearFormDataCompat()
    }

    /**
     * Deletes web view directory content. The Cookies file is kept as we clear cookies separately to avoid a crash and maintain ddg cookies.
     * Cookies may appear in files:
     *   app_webview/Cookies
     *   app_webview/Default/Cookies
     */
    private suspend fun clearWebViewDirectories(exclusions: List<String>) {
        val dataDir = context.applicationInfo.dataDir
        fileDeleter.deleteContents(File(dataDir, WEBVIEW_DATA_DIRECTORY_NAME), exclusions)

        // We don't delete the Default dir as Cookies may be inside however we do clear any other content
        fileDeleter.deleteContents(File(dataDir, WEBVIEW_DEFAULT_DIRECTORY_NAME), exclusions)
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

    companion object {
        private const val WEBVIEW_DATA_DIRECTORY_NAME = "app_webview"
        private const val WEBVIEW_DEFAULT_DIRECTORY_NAME = "app_webview/Default"
        private const val DATABASES_DIRECTORY_NAME = "databases"

        private val WEBVIEW_FILES_EXCLUDED_FROM_DELETION = listOf(
            "Default",
            "Cookies"
        )
    }
}
