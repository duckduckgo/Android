/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.common.utils.plugins.PluginPoint
import logcat.logcat
import org.json.JSONObject

class AutoconsentInterface(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val webView: WebView,
    private val autoconsentCallback: AutoconsentCallback,
) {
    @JavascriptInterface
    fun process(message: String) {
        try {
            val parsedMessage = JSONObject(message)
            val type: String = parsedMessage.getString("type")
            messageHandlerPlugins.getPlugins().firstOrNull { it.supportedTypes.contains(type) }?.process(type, message, webView, autoconsentCallback)
        } catch (e: Exception) {
            logcat { e.localizedMessage }
        }
    }

    companion object {
        const val AUTOCONSENT_INTERFACE = "AutoconsentAndroid"
    }
}
