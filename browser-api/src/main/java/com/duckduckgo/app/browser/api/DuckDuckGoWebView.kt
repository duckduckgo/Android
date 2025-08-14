/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.api

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintDocumentAdapter
import android.util.AttributeSet
import android.webkit.WebBackForwardList
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat.WebMessageListener
import org.json.JSONObject

abstract class DuckDuckGoWebView(
    context: Context,
    attrs: AttributeSet?,
) : WebView(context, attrs) {
    abstract fun removeEnableSwipeRefreshCallback()
    abstract fun safeCopyBackForwardList(): WebBackForwardList?
    abstract fun createSafePrintDocumentAdapter(documentName: String): PrintDocumentAdapter?
    abstract val safeSettings: WebSettings?
    abstract val safeHitTestResult: HitTestResult?
    abstract fun setBottomMatchingBehaviourEnabled(value: Boolean)
    abstract var isSafeWebViewEnabled: Boolean
    abstract fun setEnableSwipeRefreshCallback(callback: (Boolean) -> Unit)

    @SuppressLint("RequiresFeature", "AddWebMessageListenerUsage")
    abstract suspend fun safeAddWebMessageListener(
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebMessageListener,
    ): Boolean

    abstract suspend fun safeRemoveWebMessageListener(
        jsObjectName: String,
    ): Boolean

    abstract suspend fun safeAddDocumentStartJavaScript(
        script: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandler?

    abstract fun isDestroyed(): Boolean

    abstract suspend fun safePostMessage(
        replyProxy: JavaScriptReplyProxy,
        response: JSONObject
    )
}
