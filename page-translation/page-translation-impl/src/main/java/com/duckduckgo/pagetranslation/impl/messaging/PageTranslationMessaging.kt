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

package com.duckduckgo.pagetranslation.impl.messaging

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.AttachedWebView
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** One piece of a page's text: plain text, or HTML when [isHtml]. */
data class PageUnit(val id: Int, val text: String, val isHtml: Boolean, val isInViewport: Boolean)

/** The translation of the unit [id]: its plain text, or for an HTML unit its JSON tree. */
data class UnitTranslation(val id: Int, val translation: String, val isHtml: Boolean)

/**
 * Talks to the C-S-S pageTranslation bundle in the attached WebView. One request is in flight at a time: a new one drops the
 * previous one, whose reply is then ignored.
 */
interface PageTranslationMessaging {
    /**
     * Registers the bundle's JavaScript interface, so call it before [webView] loads its first page. Does nothing while page
     * translation is off or on a device it doesn't support, so pages don't see the interface.
     */
    @MainThread
    fun register(webView: WebView)

    /** Drops the request in flight. */
    fun cancel()

    /** Runs the bundle in the attached WebView's document whose `performance.timeOrigin` is [timeOrigin]. */
    suspend fun inject(timeOrigin: Double)

    /** Returns the page's units, or null when the bundle wasn't injected, the page doesn't reply in time or the request is dropped. */
    suspend fun extract(): List<PageUnit>?

    /** Writes [translations] into the page and returns how many units were written, or null as [extract] does. */
    suspend fun apply(pair: LanguagePair, translations: List<UnitTranslation>): Int?
}

@SingleIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class)
class RealPageTranslationMessaging @Inject constructor(
    private val attachedWebView: AttachedWebView,
    private val script: PageTranslationScript,
    private val dispatchers: DispatcherProvider,
    private val feature: PageTranslationFeature,
    private val deviceSupport: PageTranslationDeviceSupport,
) : PageTranslationMessaging {
    private val jsInterface = PageTranslationJsInterface()
    private val lock = Any()
    private var pendingRequest: PendingRequest? = null

    // a new secret per document, so a page that reads one can't recognize the user on another page
    private var injectedDocument: InjectedDocument? = null

    override fun register(webView: WebView) {
        if (!feature.translation().isEnabled() || !deviceSupport.isSupported()) return

        webView.addJavascriptInterface(jsInterface, script.interfaceName)
    }

    override fun cancel() {
        synchronized(lock) {
            pendingRequest?.reply?.complete(null)
            pendingRequest = null
        }
    }

    override suspend fun inject(timeOrigin: Double) {
        // the bundle runs once per document, so injecting into the same document again must keep its secret
        val secret = synchronized(lock) {
            injectedDocument?.takeIf { it.timeOrigin == timeOrigin }?.secret
                ?: UUID.randomUUID().toString().also { injectedDocument = InjectedDocument(timeOrigin, it) }
        }
        val source = script.get(timeOrigin, secret)
        withContext(dispatchers.main()) { attachedWebView.webView?.evaluateJavascript(source, null) }
    }

    override suspend fun extract(): List<PageUnit>? {
        val reply = request(EXTRACT, JSONObject()) ?: return null

        return try {
            val units = reply.getJSONArray(UNITS_KEY)
            List(units.length()) { i ->
                val unit = units.getJSONObject(i)
                PageUnit(unit.getInt(ID_KEY), unit.getString(TEXT_KEY), unit.optBoolean(HTML_KEY), unit.optInt(IN_VIEWPORT_KEY) == 1)
            }
        } catch (_: JSONException) {
            null
        }
    }

    override suspend fun apply(pair: LanguagePair, translations: List<UnitTranslation>): Int? {
        val updates = JSONArray()
        translations.forEach { updates.put(JSONObject().put(ID_KEY, it.id).put(if (it.isHtml) TREE_KEY else TEXT_KEY, it.translation)) }
        val params = JSONObject()
            .put(SOURCE_LANGUAGE_KEY, pair.source)
            .put(TARGET_LANGUAGE_KEY, pair.target)
            .put(UPDATES_KEY, updates)

        return request(APPLY, params)?.optInt(COUNT_KEY)
    }

    private suspend fun request(subscription: String, params: JSONObject): JSONObject? = withContext(dispatchers.main()) {
        val webView = attachedWebView.webView ?: return@withContext null
        val secret = synchronized(lock) { injectedDocument?.secret } ?: return@withContext null
        val request = PendingRequest(UUID.randomUUID().toString())
        synchronized(lock) {
            pendingRequest?.reply?.complete(null)
            pendingRequest = request
        }

        try {
            webView.evaluateJavascript(createEvent(subscription, params.put(REQUEST_ID_KEY, request.id), secret), null)
            withTimeoutOrNull(REPLY_TIMEOUT) { request.reply.await() }
        } finally {
            synchronized(lock) { if (pendingRequest === request) pendingRequest = null }
        }
    }

    private fun createEvent(subscription: String, params: JSONObject, secret: String): String {
        val event = JSONObject()
            .put(CONTEXT_KEY, CONTEXT)
            .put(FEATURE_NAME_KEY, FEATURE_NAME)
            .put(SUBSCRIPTION_NAME_KEY, subscription)
            .put(PARAMS_KEY, params)

        return "window['${script.callbackName}']('$secret', $event);"
    }

    private fun onMessage(message: String, secret: String) {
        if (secret != synchronized(lock) { injectedDocument?.secret }) return

        val params = try {
            JSONObject(message).getJSONObject(PARAMS_KEY)
        } catch (_: JSONException) {
            return
        }

        // a reply to a dropped request is ignored
        synchronized(lock) {
            pendingRequest?.takeIf { it.id == params.optString(REQUEST_ID_KEY) }?.reply?.complete(params)
        }
    }

    // a page can call it from any of its frames, so it only acts on messages that carry the secret
    inner class PageTranslationJsInterface {
        @JavascriptInterface
        fun process(message: String, secret: String) = onMessage(message, secret)
    }

    private class InjectedDocument(val timeOrigin: Double, val secret: String)

    private class PendingRequest(val id: String) {
        val reply = CompletableDeferred<JSONObject?>()
    }

    private companion object {
        val REPLY_TIMEOUT = 10.seconds
        const val CONTEXT = "contentScopeScripts"
        const val FEATURE_NAME = "pageTranslation"
        const val EXTRACT = "extract"
        const val APPLY = "apply"

        const val CONTEXT_KEY = "context"
        const val FEATURE_NAME_KEY = "featureName"
        const val SUBSCRIPTION_NAME_KEY = "subscriptionName"
        const val PARAMS_KEY = "params"
        const val REQUEST_ID_KEY = "requestId"
        const val UNITS_KEY = "units"
        const val ID_KEY = "id"
        const val TEXT_KEY = "text"
        const val HTML_KEY = "html"
        const val IN_VIEWPORT_KEY = "vp"
        const val TREE_KEY = "tree"
        const val SOURCE_LANGUAGE_KEY = "sourceLanguage"
        const val TARGET_LANGUAGE_KEY = "targetLanguage"
        const val UPDATES_KEY = "updates"
        const val COUNT_KEY = "count"
    }
}
