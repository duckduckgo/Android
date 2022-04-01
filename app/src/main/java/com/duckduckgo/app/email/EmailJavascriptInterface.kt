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
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Autofill
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import kotlinx.coroutines.runBlocking

class EmailJavascriptInterface(
    private val emailManager: EmailManager,
    private val webView: WebView,
    private val urlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider,
    private val featureToggle: FeatureToggle,
    private val autofill: Autofill,
    private val showNativeTooltip: () -> Unit
) {

    private fun getUrl(): String? {
        return runBlocking(dispatcherProvider.main()) {
            webView.url
        }
    }

    private fun isUrlFromDuckDuckGoEmail(): Boolean {
        val url = getUrl()
        return (url != null && urlDetector.isDuckDuckGoEmailUrl(url))
    }

    private fun isFeatureEnabled() = featureToggle.isFeatureEnabled(PrivacyFeatureName.AutofillFeatureName, defaultValue = true)

    @JavascriptInterface
    fun isSignedIn(): String {
        return if (isUrlFromDuckDuckGoEmail()) {
            emailManager.isSignedIn().toString()
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun getUserData(): String {
        return if (isUrlFromDuckDuckGoEmail()) {
            emailManager.getUserData()
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun storeCredentials(
        token: String,
        username: String,
        cohort: String
    ) {
        if (isUrlFromDuckDuckGoEmail()) {
            emailManager.storeCredentials(token, username, cohort)
        }
    }

    @JavascriptInterface
    fun showTooltip() {
        getUrl()?.let {
            if (isFeatureEnabled() && !autofill.isAnException(it)) {
                showNativeTooltip()
            }
        }
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "EmailInterface"
    }
}
