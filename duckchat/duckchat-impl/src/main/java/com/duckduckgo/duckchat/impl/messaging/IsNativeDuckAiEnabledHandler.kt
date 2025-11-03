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

package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.settings.api.SettingsPageFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

/**
 * Handles the isNativeDuckAiEnabled message from SERP to query Duck.ai toggle state.
 *
 * Purpose: SERP hides its Duck.ai toggle when in native browser and queries
 * native state instead, making native settings the single source of truth.
 */
@ContributesMultibinding(AppScope::class)
class IsNativeDuckAiEnabledHandler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val settingsPageFeature: SettingsPageFeature,
    private val duckChat: DuckChat,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                appScope.launch(dispatcherProvider.main()) {
                    if (settingsPageFeature.serpSettingsSync().isEnabled()) {
                        logcat { "SERP-SETTINGS: IsNativeDuckAiEnabledHandler processing message" }
                        val response = JSONObject().apply {
                            put("enabled", duckChat.isEnabled())
                        }

                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    params = response,
                                    featureName = jsMessage.featureName,
                                    method = jsMessage.method,
                                    id = id,
                                ),
                            )
                        }
                    }
                }
            }

            override val allowedDomains: List<String> = listOf(AppUrl.Url.HOST)
            override val featureName: String = "serpSettings"
            override val methods: List<String> = listOf("isNativeDuckAiEnabled")
        }
}
