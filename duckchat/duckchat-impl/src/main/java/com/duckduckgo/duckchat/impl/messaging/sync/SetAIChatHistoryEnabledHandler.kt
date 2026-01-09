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

package com.duckduckgo.duckchat.impl.messaging.sync

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatConstants
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SetAIChatHistoryEnabledHandler @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override fun process(
                jsMessage: JsMessage,
                jsMessaging: JsMessaging,
                jsMessageCallback: JsMessageCallback?,
            ) {
                if (!jsMessage.params.has(PARAM_ENABLED)) {
                    logcat(LogPriority.ERROR) { "DuckChat-Sync: ${jsMessage.method} called without 'enabled' parameter, taking no action" }
                    return
                }

                val enabledValue = jsMessage.params.opt(PARAM_ENABLED)
                if (enabledValue !is Boolean) {
                    logcat(LogPriority.ERROR) { "DuckChat-Sync: ${jsMessage.method} called with invalid 'enabled' parameter type, expected boolean" }
                    return
                }

                val enabled: Boolean = enabledValue

                logcat { "DuckChat-Sync: ${jsMessage.method} processing with enabled=$enabled" }

                appCoroutineScope.launch(dispatchers.io()) {
                    duckChatFeatureRepository.setAIChatHistoryEnabled(enabled)
                }
            }

            override val allowedDomains: List<String> =
                listOf(
                    AppUrl.Url.HOST,
                    HOST_DUCK_AI,
                )

            override val featureName: String = DuckChatConstants.JS_MESSAGING_FEATURE_NAME
            override val methods: List<String> = listOf("setAIChatHistoryEnabled")
        }

    private companion object {
        private const val PARAM_ENABLED = "enabled"
    }
}
