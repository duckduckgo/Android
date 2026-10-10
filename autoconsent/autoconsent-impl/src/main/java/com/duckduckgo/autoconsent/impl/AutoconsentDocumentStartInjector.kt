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

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject

interface AutoconsentDocumentStartInjector {
    /**
     * Registers the autoconsent script to run at document start in every frame of [webView], with a WebMessageListener
     * to talk to it. Only frames that start to load after registration completes get the script.
     *
     * @return `true` if the script and the listener were registered.
     */
    suspend fun register(webView: WebView, autoconsentCallback: AutoconsentCallback): Boolean

    /**
     * @return `true` if [register] succeeded for [webView], so evaluateJavascript injection is not needed.
     */
    fun isRegistered(webView: WebView): Boolean
}

@ContributesBinding(AppScope::class)
class RealAutoconsentDocumentStartInjector @Inject constructor(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AutoconsentDocumentStartInjector {

    private val registeredWebViews: MutableMap<WebView, Boolean> = Collections.synchronizedMap(WeakHashMap())

    override suspend fun register(webView: WebView, autoconsentCallback: AutoconsentCallback): Boolean {
        if (!isSupported()) return false

        val script = withContext(dispatcherProvider.io()) { JsReader.loadJs(BUNDLE_NAME) }
        if (script.isEmpty()) return false

        webViewCompatWrapper.addWebMessageListener(
            webView,
            JS_OBJECT_NAME,
            ALLOWED_ORIGINS,
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy,
                ) {
                    onMessage(view, message.data ?: return, isMainFrame, replyProxy, autoconsentCallback)
                }
            },
        )

        if (webViewCompatWrapper.addDocumentStartJavaScript(webView, script, ALLOWED_ORIGINS) == null) {
            webViewCompatWrapper.removeWebMessageListener(webView, JS_OBJECT_NAME)
            return false
        }

        registeredWebViews[webView] = true
        logcat { "Autoconsent: registered document start script" }
        return true
    }

    override fun isRegistered(webView: WebView): Boolean = registeredWebViews[webView] == true

    private suspend fun isSupported(): Boolean {
        return withContext(dispatcherProvider.io()) {
            webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener) &&
                webViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)
        }
    }

    // Called on the main thread, so it is safe to read webView.url here.
    private fun onMessage(
        webView: WebView,
        message: String,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
        autoconsentCallback: AutoconsentCallback,
    ) {
        val frame = AutoconsentFrame(isMainFrame = isMainFrame, topUrl = webView.url) { reply ->
            webViewCompatWrapper.postMessage(webView, replyProxy, reply)
        }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            AutoconsentInterface.dispatch(messageHandlerPlugins, message, webView, autoconsentCallback, frame)
        }
    }

    companion object {
        const val JS_OBJECT_NAME = "ddgAutoconsentObj"
        private const val BUNDLE_NAME = "autoconsent-bundle.js"
        private val ALLOWED_ORIGINS = setOf("*")
    }
}
