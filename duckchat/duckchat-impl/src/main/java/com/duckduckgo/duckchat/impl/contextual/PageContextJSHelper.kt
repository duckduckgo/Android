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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import javax.inject.Inject

interface PageContextJSHelper {

    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        data: JSONObject?,
        tabId: String,
    ): JsCallbackData?

    fun onContextualOpened(): SubscriptionEventData
}

@ContributesBinding(AppScope::class)
class RealPageContextJSHelper @Inject constructor(
    private val pageContextRepository: PageContextRepository,
) : PageContextJSHelper {
    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        data: JSONObject?,
        tabId: String,
    ): JsCallbackData? {
        if (method != METHOD_COLLECTION_RESULT) {
            return null
        }

        val serializedPageData = data?.optString(KEY_SERIALIZED_PAGE_DATA, null)
        if (!serializedPageData.isNullOrBlank()) {
            pageContextRepository.update(tabId, serializedPageData)
        } else if (data?.optBoolean(KEY_SUCCESS, true) == false) {
            pageContextRepository.clear(tabId)
        }
        return null
    }

    override fun onContextualOpened(): SubscriptionEventData {
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
        private const val KEY_SUCCESS = "success"
    }
}
