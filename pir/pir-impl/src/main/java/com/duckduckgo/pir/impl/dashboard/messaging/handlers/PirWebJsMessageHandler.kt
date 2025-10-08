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
import com.duckduckgo.pir.impl.brokers.JSONObjectAdapter
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageRequest
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.ERROR
import logcat.logcat
import org.json.JSONObject
import kotlin.reflect.KClass

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
        get() = listOf(message.messageName)
    abstract val message: PirDashboardWebMessages

    private val responseAdapter by lazy {
        Moshi.Builder().add(
            PolymorphicJsonAdapterFactory.of(PirWebMessageResponse::class.java, JSON_TYPE_PARAM)
                .withSubtype(PirWebMessageResponse.DefaultResponse::class.java, "default")
                .withSubtype(PirWebMessageResponse.HandshakeResponse::class.java, "handshake")
                .withSubtype(PirWebMessageResponse.InitialScanResponse::class.java, "initialScan")
                .withSubtype(PirWebMessageResponse.MaintenanceScanStatusResponse::class.java, "maintenanceScanStatus")
                .withSubtype(PirWebMessageResponse.GetDataBrokersResponse::class.java, "getDataBrokers")
                .withSubtype(PirWebMessageResponse.GetCurrentUserProfileResponse::class.java, "getCurrentUserProfile")
                .withSubtype(PirWebMessageResponse.GetFeatureConfigResponse::class.java, "getFeatureConfig"),
        ).add(KotlinJsonAdapterFactory())
            .build().adapter(PirWebMessageResponse::class.java)
    }

    private val requestAdapter by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).add(JSONObjectAdapter()).build()
    }

    /**
     * Uses the [PirWebMessageResponse] to construct and send a response to the [jsMessage].
     *
     * @param response The body of the response to send back, must be a subclass of [PirWebMessageResponse].
     */
    protected fun JsMessaging.sendResponse(
        jsMessage: JsMessage,
        response: PirWebMessageResponse,
    ) {
        val responseParams = kotlin.runCatching {
            response.toMessageParams()
        }.getOrElse {
            logcat(ERROR) { "PIR-WEB: Failed to serialize response: ${it.message}" }
            JSONObject() // Fallback to empty JSON object if serialization fails
        }

        onResponse(
            JsCallbackData(
                params = responseParams,
                featureName = jsMessage.featureName,
                method = jsMessage.method,
                id = jsMessage.id ?: "",
            ),
        )
    }

    private fun PirWebMessageResponse.toMessageParams(): JSONObject {
        return JSONObject(responseAdapter.toJson(this)).apply {
            // remove the param that Moshi adds as it's not needed in the response
            remove(JSON_TYPE_PARAM)
        }
    }

    /**
     * Convenience function to convert the JSON params of [JsMessage] to a specific [PirWebMessageRequest] model to avoid manually parsing the JSON.
     */
    protected fun <R : PirWebMessageRequest> JsMessage.toRequestMessage(requestClass: KClass<R>): R? = kotlin.runCatching {
        val jsonAdapter: JsonAdapter<R> = requestAdapter.adapter(requestClass.java)
        return jsonAdapter.fromJson(this.params.toString())
    }.getOrElse {
        logcat(ERROR) { "PIR-WEB: Failed to deserialize request message: ${it.message}" }
        null
    }

    companion object {
        private const val JSON_TYPE_PARAM = "type"
    }
}
