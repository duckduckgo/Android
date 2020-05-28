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

package com.duckduckgo.app.browser.logindetection

import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.LOGIN_DETECTION_INTERFACE_NAME
import com.duckduckgo.app.browser.LoginDetectionInterface
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject


class LoginDetector @Inject constructor(private val settingsDataStore: SettingsDataStore) {

    sealed class WebNavigationEvent {
        data class OnPageStarted(val webView: WebView) : WebNavigationEvent()
        data class ShouldInterceptRequest(val webView: WebView, val request: WebResourceRequest) : WebNavigationEvent()
    }

    fun addLoginDetection(webView: WebView, onLoginDetected: () -> Unit) {
        webView.addJavascriptInterface(LoginDetectionInterface { onLoginDetected() }, LOGIN_DETECTION_INTERFACE_NAME)
    }

    suspend fun onEvent(event: WebNavigationEvent) {
        if (settingsDataStore.appLoginDetection) {
            when (event) {
                is WebNavigationEvent.OnPageStarted -> injectLoginFormDetectionJS(event.webView)
                is WebNavigationEvent.ShouldInterceptRequest -> {
                    if (interceptPost(event.request)) {
                        scanPasswordFields(event.webView)
                    }
                }
            }
        }
    }

    private fun interceptPost(request: WebResourceRequest): Boolean {
        if (request.method == "POST") {
            Timber.i("LoginDetectionInterface evaluate ${request.url}")
            if (request.url?.path?.contains(Regex("login|sign-in|signin|sessions")) == true) {
                Timber.v("LoginDetectionInterface post login DETECTED")
                return true
            }
        }
        return false
    }

    private suspend fun scanPasswordFields(webView: WebView) {
        return withContext(Dispatchers.Main) {
            webView.evaluateJavascript("javascript:scanForPasswordField()", null)
        }
    }

    private fun injectLoginFormDetectionJS(webView: WebView) {
        val javascript = webView.context.resources.openRawResource(R.raw.login_form_detection).bufferedReader().use { it.readText() }
        webView.evaluateJavascript("javascript:$javascript", null)
    }
}