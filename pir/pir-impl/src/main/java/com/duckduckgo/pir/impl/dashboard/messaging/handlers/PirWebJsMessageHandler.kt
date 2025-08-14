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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import org.json.JSONObject

/**
 * Implement this class and contribute it as a multibinding to handle specific messages that are received from the PIR webview.
 *
 * Apply this annotation to your actual handler class:
 *
 * ```
 * @ContributesMultibinding(
 *     scope = ActivityScope::class,
 *     boundType = PirWebJsMessageHandler::class,
 * )
 * ```
 */
abstract class PirWebJsMessageHandler : JsMessageHandler {

    override val allowedDomains: List<String> = emptyList()
    override val featureName: String = PirDashboardWebConstants.SCRIPT_FEATURE_NAME
    override val methods: List<String>
        get() = messageNames.map { it.messageName }
    abstract val messageNames: List<PirDashboardWebMessages>

    /**
     * Constructs and sends a response to the [jsMessage] with PIR specific parameters already included.
     *
     * @param success Used to set the value of the `success` parameter in the response.
     * @param customParams Additional parameters to include in the response on top of the standard PIR parameters like API version.
     */
    protected fun JsMessaging.sendPirResponse(
        jsMessage: JsMessage,
        success: Boolean,
        customParams: Map<String, Any> = emptyMap(),
    ) {
        onResponse(
            JsCallbackData(
                // TODO This could be improved by serializing objects with Moshi
                params = JSONObject().apply {
                    put(PirDashboardWebConstants.PARAM_SUCCESS, success)
                    put(
                        PirDashboardWebConstants.PARAM_VERSION,
                        PirDashboardWebConstants.SCRIPT_API_VERSION,
                    )
                    customParams.forEach { (name, value) ->
                        put(name, value)
                    }
                },
                featureName = jsMessage.featureName,
                method = jsMessage.method,
                id = jsMessage.id ?: "",
            ),
        )
    }

    protected fun JSONObject.getStringParam(param: String): String? {
        return if (has(param)) {
            getString(param).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }
}
