/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.contentscopescripts.api.MessageHandlerPlugin
import com.duckduckgo.contentscopescripts.api.ResponseListener
import org.json.JSONObject
import timber.log.Timber

class MessagingInterface(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val webView: WebView,
    private val messageSecret: String,
    private val contentScopeScripts: ContentScopeScripts,
) : ResponseListener {
    @JavascriptInterface
    fun process(message: String, messageSecret: String) {
        if (this.messageSecret != messageSecret) return
        try {
            val parsedMessage = JSONObject(message)
            val type: String = parsedMessage.getString("type")
            messageHandlerPlugins.getPlugins().firstOrNull { it.supportedTypes.contains(type) }?.process(type, message, this)
        } catch (e: Exception) {
            Timber.d(e.localizedMessage)
        }
    }

    override fun onResponse(message: String) {
        contentScopeScripts.sendMessage(message, webView)
    }
}
