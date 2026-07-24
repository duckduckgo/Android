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

package com.duckduckgo.referral.impl.messaging

import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.referral.impl.InstallOriginBridgeFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

/**
 * Handles the `handshake` message from SERP, advertising whether the install-origin bridge is available.
 */
@ContributesMultibinding(AppScope::class)
class SerpHandshakeHandler @Inject constructor(
    private val installOriginBridgeFeature: InstallOriginBridgeFeature,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                val id = jsMessage.id ?: return

                val isEnabled = installOriginBridgeFeature.self().isEnabled() &&
                    installOriginBridgeFeature.installOriginVariant().isEnabled()

                val response = JSONObject()
                    .put(KEY_INSTALL_ORIGIN, isEnabled)
                    .put(KEY_PLATFORM, PLATFORM_ANDROID)

                jsMessaging.onResponse(
                    JsCallbackData(
                        params = response,
                        featureName = jsMessage.featureName,
                        method = jsMessage.method,
                        id = id,
                    ),
                )
            }

            override val allowedDomains: List<String> = listOf(AppUrl.Url.HOST)
            override val featureName: String = FEATURE_NAME
            override val methods: List<String> = listOf(METHOD_HANDSHAKE)
        }

    companion object {
        private const val FEATURE_NAME = "serp"
        private const val METHOD_HANDSHAKE = "handshake"
        private const val KEY_INSTALL_ORIGIN = "installOrigin"
        private const val KEY_PLATFORM = "platform"
        private const val PLATFORM_ANDROID = "android"
    }
}
