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

package com.duckduckgo.contentscopescripts.impl.features.eventhub

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

/**
 * Receives webEvent notifications from C-S-S's webDetection feature and
 * forwards them to the EventHub for telemetry processing.
 */
@ContributesMultibinding(ActivityScope::class)
class EventHubContentScopeJsMessageHandler @Inject constructor(
    private val eventHub: EventHub,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            val params = jsMessage.params
            if (params != null) {
                try {
                    val jsonParams = JSONObject(params.toString())
                    val type = jsonParams.optString("type", "")
                    if (type.isNotEmpty()) {
                        // Use a stable identifier for the tab. In the absence of a direct tab ID,
                        // we use the feature name + method as a proxy; real tab ID integration
                        // would come from the BrowserTabViewModel context.
                        val tabId = "default"
                        eventHub.handleWebEvent(type = type, tabId = tabId)
                    }
                } catch (e: Exception) {
                    // Silently fail — don't break the page
                }
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "webDetection"
        override val methods: List<String> = listOf("webEvent")
    }
}
