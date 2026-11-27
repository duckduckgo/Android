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

package com.duckduckgo.duckchat.impl.promo.messaging

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.DuckChatConstants
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class PromoCampaignContentScopeJsMessageHandler @Inject constructor(
    private val promoCampaignJSHelper: PromoCampaignJSHelper,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override val featureName: String = DuckChatConstants.JS_MESSAGING_FEATURE_NAME

        override val allowedDomains: List<String> = listOf(
            AppUrl.Url.HOST,
            duckAiHostProvider.getHost(),
        )

        override val methods: List<String> = listOf(
            PromoCampaignJSHelper.METHOD_GET_BONUS_RECORD,
            PromoCampaignJSHelper.METHOD_GET_NEW_BONUS_RECORD,
            PromoCampaignJSHelper.METHOD_BONUS_REDEEM_OUTCOME,
            PromoCampaignJSHelper.METHOD_BONUS_DISMISS,
            PromoCampaignJSHelper.METHOD_BONUS_END,
        )

        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            appCoroutineScope.launch(dispatchers.io()) {
                val response = promoCampaignJSHelper.processJsCallbackMessage(
                    featureName = featureName,
                    method = jsMessage.method,
                    requestId = jsMessage.id,
                    data = jsMessage.params,
                )
                if (response != null) {
                    jsMessaging.onResponse(response)
                }
            }
        }
    }
}
