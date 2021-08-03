/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.runBlocking

class EmailJavascriptInterface(
    private val emailManager: EmailManager,
    private val showNativeTooltip: () -> Unit,
    private val webView: WebView,
    private val urlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider
) {

    private fun isUrlFromDuckDuckGoEmail(): Boolean {
        return runBlocking(dispatcherProvider.main()) {
            val url = webView.url
            (url != null && urlDetector.isDuckDuckGoEmailUrl(url))
        }
    }

    @JavascriptInterface
    fun isSignedIn(): String {
        return if (isUrlFromDuckDuckGoEmail()) {
            emailManager.isSignedIn().toString()
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun storeCredentials(token: String, username: String, cohort: String) {
        if (isUrlFromDuckDuckGoEmail()) {
            emailManager.storeCredentials(token, username, cohort)
        }
    }

    @JavascriptInterface
    fun showTooltip() {
        showNativeTooltip()
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "EmailInterface"
    }
}
