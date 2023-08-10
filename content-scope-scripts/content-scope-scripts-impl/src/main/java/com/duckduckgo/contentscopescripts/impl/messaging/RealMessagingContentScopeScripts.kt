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

import android.os.Trace
import android.webkit.WebView
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.RealContentScopeScripts.Companion.messagingParameters
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.*
import javax.inject.Inject
import kotlin.random.Random

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealMessagingContentScopeScripts @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
) : ContentScopeScripts {

    private val messageSecret = getSecret()
    private val messageCallback = getSecret()

    private fun getSecretKeyValuePair() = "\"messageSecret\":\"$messageSecret\""
    private fun getCallbackKeyValuePair() = "\"messageCallback\":\"$messageCallback\""
    private fun getInterfaceKeyValuePair() = "\"messageInterface\":\"${messageInterface}\""

    private fun getScript(): String {
        return coreContentScopeScripts.getScript()
            .replace(messagingParameters, "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}")
    }

    override fun injectContentScopeScripts(webView: WebView) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("REAL_MESSAGING_CSS_INJECT_CSS_EVALUATE_JAVASCRIPT", traceCookie)
        if (coreContentScopeScripts.isEnabled()) {
            webView.evaluateJavascript("javascript:${getScript()}", null)
        }
        Trace.endAsyncSection("REAL_MESSAGING_CSS_INJECT_CSS_EVALUATE_JAVASCRIPT", traceCookie)
    }

    override fun addJsInterface(webView: WebView) {
        webView.addJavascriptInterface(
            MessagingInterface(messageHandlerPlugins, webView, messageSecret, this),
            messageInterface,
        )
    }

    override fun sendMessage(message: String, webView: WebView) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("REAL_MESSAGING_CSS_SEND_MESSAGE_EVALUATE_JAVASCRIPT", traceCookie)
        webView.evaluateJavascript(ReplyHandler.constructReply(message, messageCallback, messageSecret), null)
        Trace.endAsyncSection("REAL_MESSAGING_CSS_SEND_MESSAGE_EVALUATE_JAVASCRIPT", traceCookie)
    }

    companion object {
        private val messageInterface = getSecret()

        fun getSecret(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }
    }
}
