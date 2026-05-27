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

package com.duckduckgo.duckchat.impl.history

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.duckduckgo.app.di.ActivityContext
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewClient
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

interface ChatHistoryReader {
    suspend fun refresh()

    fun tearDown()
}

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class RealChatHistoryReader @Inject constructor(
    @ActivityContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @Named("ContentScopeScripts") private val contentScopeScripts: JsMessaging,
    private val duckChatWebViewClient: DuckChatWebViewClient,
    private val duckChatJSHelper: DuckChatJSHelper,
) : ChatHistoryReader {
    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    private var webView: WebView? = null
    private var pageLoadDeferred: CompletableDeferred<Unit>? = null

    override suspend fun refresh() {
        try {
            withContext(dispatchers.main()) {
                val wv = getOrCreateWebView()
                pageLoadDeferred = CompletableDeferred()
                logcat { "ChatHistoryReader: loading $DUCK_AI_URL" }
                wv.loadUrl(DUCK_AI_URL)
                val loaded = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { pageLoadDeferred?.await() }
                if (loaded == null) {
                    logcat { "ChatHistoryReader: page load timed out after ${PAGE_LOAD_TIMEOUT_MS}ms" }
                    return@withContext
                }
                logcat { "ChatHistoryReader: page loaded; SPA fan-out continues in the WebView" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "ChatHistoryReader: refresh failed — ${e.message}" }
        }
    }

    override fun tearDown() {
        pageLoadDeferred?.cancel()
        pageLoadDeferred = null
        webView?.destroy()
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebView(): WebView {
        webView?.let { return it }

        val wv = WebView(context).apply {
            settings.apply {
                userAgentString = CUSTOM_UA
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            duckChatWebViewClient.onPageFinishedListener = { url ->
                logcat { "ChatHistoryReader: onPageFinished $url" }
                pageLoadDeferred?.complete(Unit)
            }
            webViewClient = duckChatWebViewClient
            contentScopeScripts.register(
                this,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        logcat { "ChatHistoryReader: bridge $featureName.$method id=$id" }
                        if (featureName == DUCK_CHAT_FEATURE_NAME) {
                            handleDuckChatMessage(featureName, method, id, data)
                        }
                    }
                },
            )
        }

        webView = wv
        return wv
    }

    private fun handleDuckChatMessage(featureName: String, method: String, id: String?, data: JSONObject?) {
        appCoroutineScope.launch(dispatchers.io()) {
            duckChatJSHelper.processJsCallbackMessage(featureName, method, id, data)?.let { response ->
                withContext(dispatchers.main()) {
                    contentScopeScripts.onResponse(response)
                }
            }
        }
    }

    companion object {
        private const val DUCK_AI_URL = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
        private const val PAGE_LOAD_TIMEOUT_MS = 10000L
        private const val DUCK_CHAT_FEATURE_NAME = "aiChat"
    }
}
