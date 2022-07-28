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
import com.duckduckgo.app.global.plugins.PluginPoint
import org.json.JSONObject
import timber.log.Timber

class AutoconsentInterface(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val webView: WebView
) {
    @JavascriptInterface
    fun process(message: String) {
        try {
            val parsedMessage = JSONObject(message)
            val type: String = parsedMessage.getString("type")
            messageHandlerPlugins.getPlugins().firstOrNull { type == it.type }?.let { plugin ->
                plugin.process(type, message, webView)
            }
        } catch (e: Exception) {
            Timber.d("MARCOS exception is ${e.localizedMessage}")
        }
    }

    @JavascriptInterface
    fun console(message: String) {
        Timber.d("MARCOS $message")
    }
}
