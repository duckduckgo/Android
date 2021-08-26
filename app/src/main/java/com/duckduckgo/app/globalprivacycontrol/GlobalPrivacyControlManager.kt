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

package com.duckduckgo.app.globalprivacycontrol

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName

interface GlobalPrivacyControl {
    fun injectDoNotSellToDom(webView: WebView)
    fun isGpcActive(): Boolean
    fun isGpcRemoteFeatureEnabled(): Boolean
    fun getHeaders(url: String?): Map<String, String>
    fun canPerformARedirect(url: Uri): Boolean
    fun enableGpc()
    fun disableGpc()
}

class GlobalPrivacyControlManager(private val appSettingsPreferencesStore: SettingsDataStore, private val featureToggle: FeatureToggle, val gpc: Gpc) : GlobalPrivacyControl {
    private val javaScriptInjector = JavaScriptInjector()
    private val headerConsumers = listOf(
        "nytimes.com",
        "globalprivacycontrol.org",
        "global-privacy-control.glitch.me"
    )

    override fun disableGpc() {
        appSettingsPreferencesStore.globalPrivacyControlEnabled = false
    }

    override fun enableGpc() {
        appSettingsPreferencesStore.globalPrivacyControlEnabled = true
    }

    override fun isGpcRemoteFeatureEnabled(): Boolean {
        return featureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true) == true
    }

    override fun canPerformARedirect(url: Uri): Boolean {
        val domain = url.domain() ?: return false

        return if (canGpcBeUsed(domain)) {
            headerConsumers.any { UriString.sameOrSubdomain(domain, it) }
        } else {
            false
        }
    }

    override fun isGpcActive(): Boolean = appSettingsPreferencesStore.globalPrivacyControlEnabled

    override fun getHeaders(url: String?): Map<String, String> {
        return if (canGpcBeUsed(url)) {
            mapOf(GPC_HEADER to GPC_HEADER_VALUE)
        } else {
            emptyMap()
        }
    }

    @UiThread
    override fun injectDoNotSellToDom(webView: WebView) {
        if (canGpcBeUsed(webView.url)) {
            webView.evaluateJavascript("javascript:${javaScriptInjector.getFunctionsJS(webView.context)}", null)
        }
    }

    private fun canGpcBeUsed(url: String?): Boolean {
        return isGpcActive() && isGpcRemoteFeatureEnabled() && !isUrlAnException(url)
    }

    private fun isUrlAnException(url: String?): Boolean {
        if (url == null) return false
        return gpc.isAnException(url)
    }

    companion object {
        const val GPC_HEADER = "sec-gpc"
        const val GPC_HEADER_VALUE = "1"
    }

    private class JavaScriptInjector {
        private lateinit var functions: String

        fun getFunctionsJS(context: Context): String {
            if (!this::functions.isInitialized) {
                functions = context.resources.openRawResource(R.raw.donotsell).bufferedReader().use { it.readText() }
            }
            return functions
        }
    }
}
