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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader

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
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

interface ChatSuggestionsReader {
    suspend fun fetchSuggestions(query: String = ""): List<ChatSuggestion>
    fun tearDown()
}

@ContributesBinding(AppScope::class)
class RealChatSuggestionsReader @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val messaging: ChatSuggestionsJsMessaging,
) : ChatSuggestionsReader {

    private var webView: WebView? = null
    private var pageLoadDeferred: CompletableDeferred<Unit>? = null
    private var chatsResultDeferred: CompletableDeferred<JSONObject>? = null

    // Script loader state
    private var cachedScript: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> {
        return withContext(dispatchers.main()) {
            val script = getScript()
            val wv = getOrCreateWebView(script)

            val results = mutableListOf<DomainResult>()
            for (domain in DOMAINS) {
                val result = fetchFromDomain(wv, domain, query)
                if (result != null) {
                    results.add(result)
                }
            }

            val bestResult = results.maxByOrNull { result ->
                (result.pinnedChats + result.recentChats).maxOfOrNull { it.lastEdit } ?: LocalDateTime.MIN
            } ?: return@withContext emptyList()
            mergeSuggestions(bestResult.pinnedChats, bestResult.recentChats)
        }
    }

    override fun tearDown() {
        pageLoadDeferred?.cancel()
        pageLoadDeferred = null
        chatsResultDeferred?.cancel()
        chatsResultDeferred = null
        webView?.destroy()
        webView = null
    }

    // region Script Loading

    private suspend fun getScript(): String {
        return withContext(dispatchers.io()) {
            if (cachedScript.isNullOrBlank()) {
                cachedScript = loadJs(JS_FILE_NAME)
                    .replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeJson())
                    .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, "[]")
                    .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
                    .replace(
                        MESSAGING_PARAMETERS_PLACEHOLDER,
                        "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}",
                    )
            }
            cachedScript!!
        }
    }

    // TODO: Get this from privacy config instead of hardcoding
    private fun getContentScopeJson(): String {
        return """{"features":{"duckAiChatHistory":{"state":"enabled","exceptions":[],"settings":{}}},"unprotectedTemporary":[]}"""
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
    private fun getInterfaceKeyValuePair(): String = "\"javascriptInterface\":\"${ChatSuggestionsJsMessaging.JS_INTERFACE_NAME}\""

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
                        if (method == METHOD_CHATS_RESULT && data != null) {
                            chatsResultDeferred?.complete(data)
                        }
                    }
                },
            )

            webView = this
        }
    }

    // endregion

    // region Fetching

    private suspend fun fetchFromDomain(webView: WebView, domain: String, query: String): DomainResult? {
        pageLoadDeferred = CompletableDeferred()
        webView.loadDataWithBaseURL(domain, EMPTY_HTML, "text/html", "utf-8", null)
        withTimeoutOrNull(FETCH_TIMEOUT_MS) { pageLoadDeferred?.await() } ?: return null

        chatsResultDeferred = CompletableDeferred()
        val params = buildFetchParams(query)
        messaging.sendSubscriptionEvent(
            SubscriptionEventData(
                FEATURE_NAME,
                SUBSCRIPTION_GET_CHATS,
                params,
            ),
        )

        val response = withTimeoutOrNull(FETCH_TIMEOUT_MS) { chatsResultDeferred?.await() } ?: return null
        return parseResponse(response)
    }

    private fun buildFetchParams(query: String): JSONObject {
        return JSONObject().apply {
            if (query.isNotEmpty()) {
                put("query", query)
            }
            put("max_chats", MAX_SUGGESTIONS)
            if (query.isEmpty()) {
                put("since", System.currentTimeMillis() - SEVEN_DAYS_MS)
            }
        }
    }

    // endregion

    // region Parsing

    @VisibleForTesting
    internal fun parseResponse(response: JSONObject): DomainResult? {
        val success = response.optBoolean("success", false)
        if (!success) return null

        val pinnedChats = parseChats(response.optJSONArray("pinnedChats"), pinned = true)
        val recentChats = parseChats(response.optJSONArray("chats"), pinned = false)

        return DomainResult(
            pinnedChats = pinnedChats,
            recentChats = recentChats,
        )
    }

    @VisibleForTesting
    internal fun parseChats(jsonArray: org.json.JSONArray?, pinned: Boolean): List<ChatSuggestion> {
        if (jsonArray == null) return emptyList()

        return (0 until jsonArray.length()).mapNotNull { i ->
            val chat = jsonArray.optJSONObject(i) ?: return@mapNotNull null
            val chatId = chat.optString("chatId", "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = chat.optString("title", "").ifEmpty { "Untitled Chat" }
            val lastEdit = parseLastEdit(chat.optString("lastEdit", ""))

            ChatSuggestion(
                chatId = chatId,
                title = title,
                lastEdit = lastEdit,
                pinned = pinned,
            )
        }
    }

    private fun parseLastEdit(lastEditStr: String): LocalDateTime {
        if (lastEditStr.isEmpty()) return LocalDateTime.now()
        return try {
            val instant = Instant.parse(lastEditStr)
            LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        } catch (_: DateTimeParseException) {
            LocalDateTime.now()
        }
    }

    @VisibleForTesting
    internal fun mergeSuggestions(
        pinnedChats: List<ChatSuggestion>,
        recentChats: List<ChatSuggestion>,
    ): List<ChatSuggestion> {
        return (pinnedChats + recentChats)
            .sortedByDescending { it.lastEdit }
            .take(MAX_SUGGESTIONS)
    }

    // endregion

    @VisibleForTesting
    internal data class DomainResult(
        val pinnedChats: List<ChatSuggestion>,
        val recentChats: List<ChatSuggestion>,
    )

    companion object {
        private const val FEATURE_NAME = "duckAiChatHistory"
        private const val SUBSCRIPTION_GET_CHATS = "getDuckAiChats"
        private const val METHOD_CHATS_RESULT = "duckAiChatsResult"
        private const val MAX_SUGGESTIONS = 10
        private const val FETCH_TIMEOUT_MS = 3000L
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
        private const val EMPTY_HTML = "<html></html>"

        // TODO: At the time of this implementation, both domains are needed for fetching the recent chats.
        //  Once domain consolidation is deployed for Android, we can remove the duckduckgo domain.
        private val DOMAINS = listOf("https://duck.ai", "https://duckduckgo.com")

        private const val JS_FILE_NAME = "duckAiChatHistory.js"
        private const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        private const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        private const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"
        private const val MESSAGING_PARAMETERS_PLACEHOLDER = "\$ANDROID_MESSAGING_PARAMETERS$"
    }
}
