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
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.httpauth.db.HttpAuthDao
import com.duckduckgo.app.browser.httpauth.db.HttpAuthEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
}

class RealWebViewHttpAuthStore(
    private val dispatcherProvider: DispatcherProvider,
    private val fireproofWebsiteDao: FireproofWebsiteDao,
    private val httpAuthDao: HttpAuthDao
) : WebViewHttpAuthStore {
    override fun setHttpAuthUsernamePassword(webView: WebView, host: String, realm: String, username: String, password: String) {
        runBlocking {
            withContext(dispatcherProvider.io()) {
                httpAuthDao.insert(
                    HttpAuthEntity(
                        host = host,
                        realm = realm,
                        username = username,
                        password = password
                    )
                )
            }
        }
    }

    override fun getHttpAuthUsernamePassword(webView: WebView, host: String, realm: String): WebViewHttpAuthCredentials? {
        return runBlocking(dispatcherProvider.io()) {
            val credentials = httpAuthDao.getAuthCredentials(host = host, realm = realm) ?: return@runBlocking null
            if (credentials.username == null || credentials.password == null) return@runBlocking null
            return@runBlocking WebViewHttpAuthCredentials(credentials.username!!, credentials.password!!)
        }
    }

    override fun clearHttpAuthUsernamePassword(webView: WebView) {
        runBlocking(dispatcherProvider.io()) {
            val exclusions = fireproofWebsiteDao.fireproofWebsitesSync().map { it.website() }
            httpAuthDao.deleteAll(exclusions)
        }
    }
}
