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

package com.duckduckgo.subscriptions.impl.messaging

import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Isolated-world mirror of the page-world useSubscription handlers.
 *
 * The page-world handlers live under contextName "subscriptionPages" / featureName
 * "useSubscription" and are unreachable from the C-S-S message bridge. This plugin
 * is registered in ContentScopeScripts (isolated world) so bridged pages can call
 * the same useSubscription methods.
 */
@ContributesMultibinding(AppScope::class)
class UseSubscriptionContentScopeJsMessageHandler @Inject constructor() : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = listOf(
            AppUrl.Url.HOST,
            AppUrl.Url.DUCK_AI_HOST,
        )

        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf(
            "getSubscription",
            "setSubscription",
            "getSubscriptionOptions",
            "getSubscriptionTierOptions",
            "setAuthTokens",
            "getAuthAccessToken",
            "getFeatureConfig",
            "subscriptionSelected",
            "subscriptionChangeSelected",
            "activateSubscription",
            "featureSelected",
            "backToSettings",
            "backToSettingsActivateSuccess",
            "getAccessToken",
            "subscriptionsMonthlyPriceClicked",
            "subscriptionsYearlyPriceClicked",
            "subscriptionsUnknownPriceClicked",
            "subscriptionsAddEmailSuccess",
            "subscriptionsWelcomeAddEmailClicked",
            "subscriptionsWelcomeFaqClicked",
        )
    }
}
