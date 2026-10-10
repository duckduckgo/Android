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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import com.duckduckgo.autoconsent.impl.handlers.ReplyHandler

/**
 * The frame that sent an autoconsent message, and the channel to reply to that same frame.
 *
 * @property isMainFrame true if the message came from the top-level document.
 * @property topUrl URL of the top-level document, or null when only the sender's own URL is known.
 */
class AutoconsentFrame(
    val isMainFrame: Boolean,
    val topUrl: String?,
    private val sender: suspend (String) -> Unit,
) {
    /**
     * Sends [message] (a JSON object) to the autoconsent instance in this frame.
     * Must be called on the main thread.
     */
    suspend fun reply(message: String) = sender(message)

    companion object {
        /**
         * The script was injected with evaluateJavascript, which only reaches the top-level document.
         */
        fun legacy(webView: WebView): AutoconsentFrame = AutoconsentFrame(isMainFrame = true, topUrl = null) { message ->
            webView.evaluateJavascript("javascript:${ReplyHandler.constructReply(message)}", null)
        }
    }
}
