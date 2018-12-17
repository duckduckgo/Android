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
        webView.clearCache(true)
        webView.clearHistory()
        webStorage.deleteAllData()
        webView.clearFormData()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            clearFormData(WebViewDatabase.getInstance(context))
        }
    }

    /**
     * Deprecated and not needed on Oreo or later
     */
    @Suppress("DEPRECATION")
    private fun clearFormData(webViewDatabase: WebViewDatabase) {
        webViewDatabase.clearFormData()
    }

    override suspend fun clearExternalCookies() {
        cookieManager.removeExternalCookies()
    }

    override fun clearWebViewSessions() {
        webViewSessionStorage.deleteAllSessions()
    }
}
