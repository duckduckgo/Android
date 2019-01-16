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
import com.duckduckgo.app.global.performance.measureExecution
import javax.inject.Inject

interface WebDataManager {
    suspend fun clearExternalCookies()
    fun clearData(webView: WebView, webStorage: WebStorage, context: Context)
    fun clearWebViewSessions()
}

class WebViewDataManager @Inject constructor(
    private val webViewSessionStorage: WebViewSessionStorage,
    private val cookieManager: DuckDuckGoCookieManager
) : WebDataManager {

    override fun clearData(webView: WebView, webStorage: WebStorage, context: Context) {
        clearCache(webView)
        clearHistory(webView)
        clearWebStorage(webStorage)
        clearFormData(webView)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            clearFormData(WebViewDatabase.getInstance(context))
        }
    }

    private fun clearCache(webView: WebView) {
        measureExecution("Cleared global WebView cache") {
            webView.clearCache(true)
        }
    }

    private fun clearHistory(webView: WebView) {
        measureExecution("Cleared history") {
            webView.clearHistory()
        }
    }

    private fun clearWebStorage(webStorage: WebStorage) {
        measureExecution("Cleared web storage") {
            webStorage.deleteAllData()
        }
    }

    private fun clearFormData(webView: WebView) {
        measureExecution("Cleared web view form data") {
            webView.clearFormData()
        }
    }

    /**
     * Deprecated and not needed on Oreo or later
     */
    @Suppress("DEPRECATION")
    private fun clearFormData(webViewDatabase: WebViewDatabase) {
        measureExecution("Cleared legacy form data") {
            webViewDatabase.clearFormData()
        }
    }

    override suspend fun clearExternalCookies() {
        measureExecution("Cleared cookies") {
            cookieManager.removeExternalCookies()
        }
    }

    override fun clearWebViewSessions() {
        measureExecution("Cleared web view sessions") {
            webViewSessionStorage.deleteAllSessions()
        }
    }
}
