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

package com.duckduckgo.duckchat.impl.contextual

import android.os.SystemClock
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.ContextType
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Reason
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Trigger
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface PageContextJSHelper {

    suspend fun processPageContext(
        featureName: String,
        method: String,
        data: JSONObject?,
        tabId: String,
    ): String?

    fun cancelCollection(tabId: String)

    fun collectPageContext(tabId: String = "", trigger: Trigger = Trigger.TAB_CONTENT): SubscriptionEventData
}

@ContributesBinding(AppScope::class)
class RealPageContextJSHelper @Inject constructor(
    private val pixels: PageContextExtractionPixels,
    private val feature: DuckChatFeature,
    @AppCoroutineScope private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : PageContextJSHelper {
    private data class Request(val started: Long, val trigger: Trigger)
    private val requests = ConcurrentHashMap<String, Request>()
    override suspend fun processPageContext(
        featureName: String,
        method: String,
        data: JSONObject?,
        tabId: String,
    ): String? {
        if (method != METHOD_COLLECTION_RESULT) {
            return null
        }

        val serializedPageData = data?.optString(KEY_SERIALIZED_PAGE_DATA, null)
        requests.remove(tabId)?.let { request ->
            val json = serializedPageData?.let { runCatching { JSONObject(it) }.getOrNull() }
            val reason = when {
                serializedPageData.isNullOrBlank() -> Reason.EMPTY_CONTENT
                json == null -> Reason.DESERIALIZE_FAILED
                json.optString("content").isBlank() -> Reason.EMPTY_CONTENT
                else -> null
            }
            if (reason == null) {
                pixels.success(ContextType.MARKDOWN)
            } else {
                pixels.failed(reason, request.trigger, ContextType.MARKDOWN, SystemClock.elapsedRealtime() - request.started)
            }
        }
        if (!serializedPageData.isNullOrBlank()) {
            return serializedPageData
        }
        return null
    }

    override fun cancelCollection(tabId: String) {
        requests.remove(tabId)
    }

    override fun collectPageContext(tabId: String, trigger: Trigger): SubscriptionEventData {
        if (feature.pdfPageContext().isEnabled()) {
            val request = Request(SystemClock.elapsedRealtime(), trigger)
            requests[tabId] = request
            scope.launch(dispatchers.io()) {
                delay(30_000)
                if (requests.remove(tabId, request) && feature.pdfPageContext().isEnabled()) {
                    pixels.failed(Reason.TIMEOUT, trigger, ContextType.MARKDOWN)
                }
            }
        }
        return SubscriptionEventData(
            PAGE_CONTEXT_FEATURE_NAME,
            SUBSCRIPTION_COLLECT,
            JSONObject(),
        )
    }

    companion object {
        const val PAGE_CONTEXT_FEATURE_NAME = "pageContext"
        private const val SUBSCRIPTION_COLLECT = "collect"
        private const val METHOD_COLLECTION_RESULT = "collectionResult"
        private const val KEY_SERIALIZED_PAGE_DATA = "serializedPageData"
    }
}
