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

package com.duckduckgo.duckchat.impl.clearing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleterJsMessaging.Companion.FEATURE_NAME
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleterJsMessaging.Companion.METHOD_CLEAR_DATA_COMPLETED
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleterJsMessaging.Companion.METHOD_CLEAR_DATA_FAILED
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleterJsMessaging.Companion.METHOD_CLEAR_DATA_READY
import com.duckduckgo.duckchat.impl.feature.DuckAiDataClearingFeature
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

interface DuckChatDeleter {
    suspend fun deleteChat(chatId: String): Boolean
    suspend fun deleteAllChats(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatDeleter @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val messaging: DuckChatDeleterJsMessaging,
    private val duckAiDataClearingFeature: DuckAiDataClearingFeature,
    duckAiHostProvider: DuckAiHostProvider,
) : DuckChatDeleter {

    private val mutex = Mutex()

    private var webView: WebView? = null
    private var pageLoadDeferred: CompletableDeferred<Unit>? = null
    private var readyDeferred: CompletableDeferred<Unit>? = null
    private var clearResultDeferred: CompletableDeferred<Boolean>? = null

    private val domains: List<String> = listOf("https://${duckAiHostProvider.getHost()}", "https://duckduckgo.com")

    // Script loader state
    private var cachedScript: String? = null

    override suspend fun deleteChat(chatId: String): Boolean {
        if (!duckAiDataClearingFeature.self().isEnabled()) return false

        return mutex.withLock {
            withContext(dispatchers.main()) {
                try {
                    val script = getScript()
                    val wv = getOrCreateWebView(script)

                    var allSucceeded = true
                    for (domain in domains) {
                        val success = clearFromDomain(wv, domain, chatId)
                        if (!success) {
                            logcat { "DuckChatDeleter: clearing failed for domain $domain, chatId $chatId" }
                            allSucceeded = false
                        }
                    }
                    allSucceeded
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logcat { "DuckChatDeleter: deleteChat failed with ${e.message}" }
                    false
                } finally {
                    tearDown()
                }
            }
        }
    }

    override suspend fun deleteAllChats(): Boolean {
        logcat { "deleteAllChats is no-op for the webview legacy implementation" }
        return true // no-op as we don't clear IDB data from here
    }

    private fun tearDown() {
        pageLoadDeferred?.cancel()
        pageLoadDeferred = null
        readyDeferred?.cancel()
        readyDeferred = null
        clearResultDeferred?.cancel()
        clearResultDeferred = null
        webView?.destroy()
        webView = null
        cachedScript = null
    }

    // region Script Loading

    private suspend fun getScript(): String {
        return withContext(dispatchers.io()) {
            cachedScript.takeUnless { it.isNullOrBlank() } ?: loadJs(JS_FILE_NAME)
                .replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeJson())
                .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, "[]")
                .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
                .replace(
                    MESSAGING_PARAMETERS_PLACEHOLDER,
                    "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}",
                ).also { cachedScript = it }
        }
    }

    @VisibleForTesting
    internal fun getContentScopeJson(): String {
        val toggle = duckAiDataClearingFeature.self()
        val state = if (toggle.isEnabled()) "enabled" else "disabled"
        val settings = toggle.getSettings() ?: DEFAULT_SETTINGS
        val exceptions = JSONArray().apply {
            toggle.getExceptions().forEach { exception ->
                put(
                    JSONObject().apply {
                        put("domain", exception.domain)
                        exception.reason?.let { put("reason", it) }
                    },
                )
            }
        }
        return """{"features":{"duckAiDataClearing":{"state":"$state","exceptions":$exceptions,"settings":$settings}},"unprotectedTemporary":[]}"""
    }

    private fun getUserPreferencesJson(): String {
        val params = "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()},${getLanguageKeyValuePair()}," +
            "${getSessionKeyValuePair()},${getDesktopModeKeyValuePair()},$MESSAGING_PARAMETERS_PLACEHOLDER"
        return "{$params}"
    }

    private fun getVersionNumberKeyValuePair() = "\"versionNumber\":${appBuildConfig.versionCode}"
    private fun getPlatformKeyValuePair() = "\"platform\":{\"name\":\"android\"}"
    private fun getLanguageKeyValuePair() = "\"locale\":\"${Locale.getDefault().language}\""
    private fun getSessionKeyValuePair() = "\"sessionKey\":\"sessionKey\""
    private fun getDesktopModeKeyValuePair() = "\"desktopModeEnabled\":false"
    private fun getSecretKeyValuePair(): String = "\"messageSecret\":\"${messaging.secret}\""
    private fun getCallbackKeyValuePair(): String = "\"messageCallback\":\"${messaging.callbackName}\""
    private fun getInterfaceKeyValuePair(): String = "\"javascriptInterface\":\"${DuckChatDeleterJsMessaging.JS_INTERFACE_NAME}\""

    private fun loadJs(resourceName: String): String {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    // endregion

    // region WebView Setup

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebView(script: String): WebView {
        webView?.let { return it }

        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript("javascript:$script", null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageLoadDeferred?.complete(Unit)
                }
            }

            messaging.register(
                this,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        when (method) {
                            METHOD_CLEAR_DATA_READY -> readyDeferred?.complete(Unit)
                            METHOD_CLEAR_DATA_COMPLETED -> clearResultDeferred?.complete(true)
                            METHOD_CLEAR_DATA_FAILED -> {
                                val error = data?.optString("error", "unknown")
                                logcat { "DuckChatDeleter: clear data failed with error: $error" }
                                clearResultDeferred?.complete(false)
                            }
                        }
                    }
                },
            )

            webView = this
        }
    }

    // endregion

    // region Clearing

    private suspend fun clearFromDomain(webView: WebView, domain: String, chatId: String): Boolean {
        // Step 1: Load empty HTML with base URL
        pageLoadDeferred = CompletableDeferred()
        readyDeferred = CompletableDeferred()
        webView.loadDataWithBaseURL(domain, EMPTY_HTML, "text/html", "utf-8", null)

        // Step 2: Wait for page load
        withTimeoutOrNull(STEP_TIMEOUT_MS) { pageLoadDeferred?.await() } ?: return false

        // Step 3: Wait for duckAiClearDataReady notification
        withTimeoutOrNull(STEP_TIMEOUT_MS) { readyDeferred?.await() } ?: return false

        // Step 4: Send duckAiClearData subscription with chatId params
        clearResultDeferred = CompletableDeferred()
        val params = JSONObject().apply {
            put("chatId", chatId)
        }
        messaging.sendSubscriptionEvent(
            SubscriptionEventData(
                FEATURE_NAME,
                SUBSCRIPTION_CLEAR_DATA,
                params,
            ),
        )

        // Step 5: Wait for duckAiClearDataCompleted or duckAiClearDataFailed
        return withTimeoutOrNull(STEP_TIMEOUT_MS) { clearResultDeferred?.await() } ?: false
    }

    // endregion

    companion object {
        private const val SUBSCRIPTION_CLEAR_DATA = "duckAiClearData"
        private const val STEP_TIMEOUT_MS = 5000L
        private const val EMPTY_HTML = "<html></html>"
        private const val JS_FILE_NAME = "duckAiDataClearing.js"
        private const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        private const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        private const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"
        private const val MESSAGING_PARAMETERS_PLACEHOLDER = "\$ANDROID_MESSAGING_PARAMETERS$"
        private const val DEFAULT_SETTINGS =
            """{"chatsLocalStorageKeys":["savedAIChats"],""" +
                """"chatImagesIndexDbNameObjectStoreNamePairs":""" +
                """[["savedAIChatData","chat-images"],["savedAIChatData","saved-chats"]]}"""
    }
}
