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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.pixels.EventHubPixelManager
import com.duckduckgo.eventhub.impl.webevents.WebEventsFeatureName
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class EventHubContentScopeJsMessageHandler @Inject constructor(
    private val pixelManager: EventHubPixelManager,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            val eventType = jsMessage.params.optString("type", "")
            if (eventType.isEmpty()) {
                logcat(WARN) { "webEvent message missing 'type' parameter" }
                return
            }

            val webViewId = jsMessage.params.optJSONObject("nativeData")?.optString("webViewId", "") ?: ""

            logcat(VERBOSE) { "EventHub: received webEvent type=$eventType webViewId=$webViewId" }
            pixelManager.handleWebEvent(jsMessage.params, webViewId)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = WebEventsFeatureName.WebEvents.value
        override val methods: List<String> = listOf("webEvent")
    }
}
