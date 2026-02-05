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

package com.duckduckgo.webdetection.impl

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Handles messages from the webDetection feature in Content Scope Scripts.
 *
 * This handler processes:
 * - `fireTelemetry`: Fires telemetry events (e.g., for adwall detection)
 * - `detectionBreakageData`: Notifies about detections for inclusion in breakage reports
 */
@ContributesMultibinding(AppScope::class)
class WebDetectionContentScopeJsMessageHandler @Inject constructor(
    private val telemetryManager: WebDetectionTelemetryManager,
    private val breakageDataStore: WebDetectionBreakageDataStore,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            when (jsMessage.method) {
                METHOD_FIRE_TELEMETRY -> handleFireTelemetry(jsMessage)
                METHOD_DETECTION_BREAKAGE_DATA -> handleDetectionBreakageData(jsMessage)
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = FEATURE_NAME
        override val methods: List<String> = listOf(METHOD_FIRE_TELEMETRY, METHOD_DETECTION_BREAKAGE_DATA)
    }

    private fun handleFireTelemetry(jsMessage: JsMessage) {
        val type = jsMessage.params.optString("type")
        val detectorId = jsMessage.params.optString("detectorId")

        if (type.isNotEmpty() && detectorId.isNotEmpty()) {
            telemetryManager.handleTelemetry(type, detectorId)
        }
    }

    private fun handleDetectionBreakageData(jsMessage: JsMessage) {
        val detectorId = jsMessage.params.optString("detectorId")

        if (detectorId.isNotEmpty()) {
            breakageDataStore.addDetection(detectorId)
        }
    }

    companion object {
        const val FEATURE_NAME = "webDetection"
        const val METHOD_FIRE_TELEMETRY = "fireTelemetry"
        const val METHOD_DETECTION_BREAKAGE_DATA = "detectionBreakageData"
    }
}
