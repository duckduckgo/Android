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

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

class WebDataManager(private val host: String) {

    fun clearData(webView: WebView, webStorage: WebStorage) {
        webView.clearCache(true)
        webView.clearHistory()
        webStorage.deleteAllData()
    }

    fun clearExternalCookies(cookieManager: CookieManager, clearAllCallback: (() -> Unit)) {

        val ddgCookie = cookieManager.getCookie(host)?.split(";")

        cookieManager.removeAllCookies {
            ddgCookie?.forEach { cookieManager.setCookie(host, it.trim()) }
            clearAllCallback()
        }
    }
}
