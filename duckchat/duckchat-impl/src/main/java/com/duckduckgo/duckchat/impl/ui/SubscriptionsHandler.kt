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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.api.SubscriptionsJSHelper
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SubscriptionsHandler @Inject constructor(
    private val subscriptionsJSHelper: SubscriptionsJSHelper,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatcherProvider: DispatcherProvider,
) {

    fun handleSubscriptionsFeature(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        context: Context,
        appCoroutineScope: CoroutineScope,
        contentScopeScripts: JsMessaging,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val response = subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data)
            withContext(dispatcherProvider.main()) {
                response?.let {
                    contentScopeScripts.onResponse(response)
                }
            }

            when (method) {
                METHOD_BACK_TO_SETTINGS -> {
                    withContext(dispatcherProvider.main()) {
                        globalActivityStarter.start(context, SubscriptionsSettingsScreenWithEmptyParams)
                    }
                }

                METHOD_OPEN_SUBSCRIPTION_ACTIVATION -> {
                    withContext(dispatcherProvider.main()) {
                        globalActivityStarter.start(context, RestoreSubscriptionScreenWithParams(isOriginWeb = true))
                    }
                }

                METHOD_OPEN_SUBSCRIPTION_PURCHASE -> {
                    val subscriptionParams = runCatching {
                        data?.getString(MESSAGE_PARAM_ORIGIN_KEY).takeUnless { it.isNullOrBlank() }
                            ?.let { nonEmptyOrigin ->
                                SubscriptionPurchase(nonEmptyOrigin)
                            } ?: SubscriptionPurchase()
                    }.getOrDefault(SubscriptionPurchase())

                    withContext(dispatcherProvider.main()) {
                        globalActivityStarter.start(context, subscriptionParams)
                    }
                }

                else -> {}
            }
        }
    }

    companion object {
        private const val METHOD_BACK_TO_SETTINGS = "backToSettings"
        private const val METHOD_OPEN_SUBSCRIPTION_ACTIVATION = "openSubscriptionActivation"
        private const val METHOD_OPEN_SUBSCRIPTION_PURCHASE = "openSubscriptionPurchase"
        private const val MESSAGE_PARAM_ORIGIN_KEY = "origin"
    }
}
