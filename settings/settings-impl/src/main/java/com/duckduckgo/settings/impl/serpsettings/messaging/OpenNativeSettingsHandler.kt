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

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Handles the openNativeSettings message from SERP to open native settings screens.
 */
@ContributesMultibinding(AppScope::class)
class OpenNativeSettingsHandler @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val globalActivityStarter: GlobalActivityStarter,
    private val settingsPageFeature: SettingsPageFeature,
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
                        logcat { "SERP-SETTINGS: OpenNativeSettingsHandler processing message" }
                        val params = jsMessage.params

                        when (val screenParam = params.optString("screen", "")) {
                            AI_FEATURES_SCREEN_NAME -> {
                                val intent = globalActivityStarter.startIntent(context, DuckChatNativeSettingsNoParams)
                                intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                            else -> {
                                logcat(WARN) { "No action for given screen param: $screenParam" }
                            }
                        }
                    }
                }
            }

            override val allowedDomains: List<String> = listOf(AppUrl.Url.HOST)
            override val featureName: String = "serpSettings"
            override val methods: List<String> = listOf("openNativeSettings")
        }

    companion object {
        private const val AI_FEATURES_SCREEN_NAME = "aiFeatures"
    }
}
