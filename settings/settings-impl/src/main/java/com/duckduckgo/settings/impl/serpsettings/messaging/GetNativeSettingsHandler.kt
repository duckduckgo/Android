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

package com.duckduckgo.settings.impl.serpsettings.messaging

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.settings.impl.serpsettings.store.SerpSettingsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

/**
 * Handles the getNativeSettings message from SERP to retrieve stored settings.
 *
 * If there are no stored settings, it returns an empty JSON object according to the expected format.
 */
@ContributesMultibinding(AppScope::class)
class GetNativeSettingsHandler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val settingsPageFeature: SettingsPageFeature,
    private val serpSettingsDataStore: SerpSettingsDataStore,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                appScope.launch(dispatcherProvider.io()) {
                    if (settingsPageFeature.serpSettingsSync().isEnabled()) {
                        logcat { "SERP-SETTINGS: GetNativeSettingsHandler processing message" }

                        val settingsString = serpSettingsDataStore.getSerpSettings()

                        val settingsJsonObject = if (settingsString.isNullOrEmpty()) {
                            // Return an empty JSON object if no settings are stored
                            JSONObject()
                        } else {
                            JSONObject(settingsString)
                        }

                        logcat { "SERP-SETTINGS: GetNativeSettingsHandler sending: $settingsJsonObject" }
                        jsMessaging.onResponse(
                            JsCallbackData(
                                params = settingsJsonObject,
                                featureName = jsMessage.featureName,
                                method = jsMessage.method,
                                id = jsMessage.id ?: "",
                            ),
                        )
                    }
                }
            }

            override val allowedDomains: List<String> = listOf(AppUrl.Url.HOST)
            override val featureName: String = "serpSettings"
            override val methods: List<String> = listOf(GET_NATIVE_SETTINGS_METHOD_NAME)
        }

    companion object {
        private const val GET_NATIVE_SETTINGS_METHOD_NAME = "getNativeSettings"
    }
}
