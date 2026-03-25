/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.bridge.impl

import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.bridge.api.DuckAiBridgeManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiBridgeManager @Inject constructor(
    private val handlers: DaggerSet<DuckAiBridgeHandler>,
) : DuckAiBridgeManager {

    @VisibleForTesting
    internal val registeredHandlers = mutableMapOf<String, DuckAiBridgeHandler>()

    override fun attachToWebView(webView: WebView) {
        if (handlers.isEmpty()) return
        handlers.forEach { handler ->
            registeredHandlers[handler.bridgeName] = handler
            runCatching {
                WebViewCompat.addWebMessageListener(webView, handler.bridgeName, ALLOWED_ORIGINS) { _, message, _, _, replyProxy ->
                    val data = message?.data ?: return@addWebMessageListener
                    handler.onMessage(data, replyProxy)
                }
            }
        }
    }

    override fun detachFromWebView(webView: WebView) {
        // addWebMessageListener has no removal API; listeners are released with the WebView.
    }

    @VisibleForTesting
    internal fun simulateMessage(bridgeName: String, message: String, replyProxy: JavaScriptReplyProxy) {
        val handler = registeredHandlers[bridgeName] ?: error("No handler registered for '$bridgeName'")
        handler.onMessage(message, replyProxy)
    }

    companion object {
        private val ALLOWED_ORIGINS = setOf("https://duck.ai", "https://duckduckgo.com")
    }
}
