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
import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.db.SettingsDataStore

interface GlobalPrivacyControl {
    fun injectDoNotSellToDom(webView: WebView)
    fun isGpcActive(): Boolean
    fun getHeaders(): Map<String, String>
}

class GlobalPrivacyControlManager(private val appSettingsPreferencesStore: SettingsDataStore) : GlobalPrivacyControl {
    private val javaScriptInjector = JavaScriptInjector()

    override fun isGpcActive(): Boolean = appSettingsPreferencesStore.globalPrivacyControlEnabled

    override fun getHeaders(): Map<String, String> {
        return if (appSettingsPreferencesStore.globalPrivacyControlEnabled) {
            mapOf(GPC_HEADER to GPC_HEADER_VALUE)
        } else {
            emptyMap()
        }
    }

    @UiThread
    override fun injectDoNotSellToDom(webView: WebView) {
        if (appSettingsPreferencesStore.globalPrivacyControlEnabled) {
            webView.evaluateJavascript("javascript:${javaScriptInjector.getFunctionsJS(webView.context)}", null)
        }
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
