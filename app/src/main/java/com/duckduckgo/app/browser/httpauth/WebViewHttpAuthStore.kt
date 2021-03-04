/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.httpauth

import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.fire.DatabaseCleaner

data class WebViewHttpAuthCredentials(val username: String, val password: String)

// Methods are marked to run in the UiThread because it is the thread of webview
// if necessary the method impls will change thread to access the http auth dao
interface WebViewHttpAuthStore {
    @UiThread
    fun setHttpAuthUsernamePassword(webView: WebView, host: String, realm: String, username: String, password: String)
    @UiThread
    fun getHttpAuthUsernamePassword(webView: WebView, host: String, realm: String): WebViewHttpAuthCredentials?
    @UiThread
    fun clearHttpAuthUsernamePassword(webView: WebView)
    @WorkerThread
    suspend fun cleanHttpAuthDatabase()
}

class RealWebViewHttpAuthStore(
    private val webViewDatabase: WebViewDatabase,
    private val appDatabaseCleaner: DatabaseCleaner
) : WebViewHttpAuthStore {
    override fun setHttpAuthUsernamePassword(webView: WebView, host: String, realm: String, username: String, password: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webViewDatabase.setHttpAuthUsernamePassword(host, realm, username, password)
        } else {
            webView.setHttpAuthUsernamePassword(host, realm, username, password)
        }
    }

    override fun getHttpAuthUsernamePassword(webView: WebView, host: String, realm: String): WebViewHttpAuthCredentials? {
        val credentials = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webViewDatabase.getHttpAuthUsernamePassword(host, realm)
        } else {
            @Suppress("DEPRECATION")
            webView.getHttpAuthUsernamePassword(host, realm)
        } ?: return null

        return WebViewHttpAuthCredentials(username = credentials[0], password = credentials[1])
    }

    override fun clearHttpAuthUsernamePassword(webView: WebView) {
        webViewDatabase.clearHttpAuthUsernamePassword()
    }

    override suspend fun cleanHttpAuthDatabase() {
        appDatabaseCleaner.cleanDatabase()
    }
}
