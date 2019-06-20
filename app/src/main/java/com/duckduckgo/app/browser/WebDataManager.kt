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
import android.os.Build
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.global.file.FileDeleter
import java.io.File
import javax.inject.Inject

interface WebDataManager {
    suspend fun clearExternalCookies()
    fun clearData(webView: WebView, webStorage: WebStorage, webViewDatabase: WebViewDatabase)
    fun clearWebViewSessions()
    suspend fun deleteWebViewDirectory()
}

class WebViewDataManager @Inject constructor(
    private val context: Context,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val cookieManager: DuckDuckGoCookieManager,
    private val fileDeleter: FileDeleter
) : WebDataManager {

    override fun clearData(webView: WebView, webStorage: WebStorage, webViewDatabase: WebViewDatabase) {
        clearCache(webView)
        clearHistory(webView)
        clearWebStorage(webStorage)
        clearFormData(webView)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            clearFormData(webViewDatabase)
        }

        clearAuthentication(webViewDatabase)
    }

    private fun clearCache(webView: WebView) {
        webView.clearCache(true)
    }

    private fun clearHistory(webView: WebView) {
        webView.clearHistory()
    }

    private fun clearWebStorage(webStorage: WebStorage) {
        webStorage.deleteAllData()
    }

    private fun clearFormData(webView: WebView) {
        webView.clearFormData()
    }

    override suspend fun deleteWebViewDirectory() {
        val webViewDataDirectory = File(context.applicationInfo.dataDir, WEBVIEW_DATA_DIRECTORY_NAME)
        fileDeleter.deleteContents(webViewDataDirectory, FILENAMES_EXCLUDED_FROM_DELETION)
    }

    /**
     * Deprecated and not needed on Oreo or later
     */
    @Suppress("DEPRECATION")
    private fun clearFormData(webViewDatabase: WebViewDatabase) {
        webViewDatabase.clearFormData()
    }

    private fun clearAuthentication(webViewDatabase: WebViewDatabase) {
        webViewDatabase.clearHttpAuthUsernamePassword()
    }

    override suspend fun clearExternalCookies() {
        cookieManager.removeExternalCookies()
    }

    override fun clearWebViewSessions() {
        webViewSessionStorage.deleteAllSessions()
    }

    companion object {
        private const val WEBVIEW_DATA_DIRECTORY_NAME = "app_webview"

        private val FILENAMES_EXCLUDED_FROM_DELETION = listOf(
            "Cookies"
        )
    }
}
