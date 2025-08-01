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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.subscriptions.api.SubscriptionsJSHelper
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@ContributesBinding(AppScope::class)
class RealSubscriptionsJSHelper @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val privacyProFeature: PrivacyProFeature,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionsJSHelper {

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): JsCallbackData? = withContext(dispatcherProvider.io()) {
        when (method) {
            METHOD_HANDSHAKE -> id?.let {
                val availableMethods = if (privacyProFeature.duckAISubscriptionMessaging().isEnabled()) {
                    JSONArray().apply {
                        put(SUBSCRIPTION_DETAILS)
                        put(GET_AUTH_ACCESS_TOKEN)
                        put(GET_FEATURE_CONFIG)
                        put(AUTH_UPDATE)
                    }
                } else {
                    JSONArray().apply {
                        put(SUBSCRIPTION_DETAILS)
                    }
                }
                val jsonPayload = JSONObject().apply {
                    put(
                        AVAILABLE_MESSAGES,
                        availableMethods,
                    )
                    put(PLATFORM, ANDROID)
                }
                return@withContext JsCallbackData(jsonPayload, featureName, method, id)
            }

            METHOD_SUBSCRIPTION_DETAILS -> id?.let {
                getSubscriptionDetailsData(featureName, method, it)
            }

            METHOD_GET_AUTH_ACCESS_TOKEN -> id?.let {
                if (privacyProFeature.duckAISubscriptionMessaging().isEnabled().not()) return@withContext null
                getAuthAccessTokenData(featureName, method, it)
            }

            METHOD_GET_FEATURE_CONFIG -> id?.let {
                if (privacyProFeature.duckAISubscriptionMessaging().isEnabled().not()) return@withContext null
                getFeatureConfigData(featureName, method, it)
            }

            else -> null
        }
    }

    private suspend fun getSubscriptionDetailsData(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val jsonPayload = subscriptionsManager.getSubscription()?.let { userSubscription ->
            JSONObject().apply {
                put(IS_SUBSCRIBED, userSubscription.isActive())
                put(BILLING_PERIOD, userSubscription.billingPeriod)
                put(STARTED_AT, userSubscription.startedAt)
                put(EXPIRES_OR_RENEWS_AT, userSubscription.expiresOrRenewsAt)
                put(PAYMENT_PLATFORM, userSubscription.platform)
                put(STATUS, userSubscription.status.statusName)
            }
        } ?: JSONObject().apply {
            put(IS_SUBSCRIBED, false)
        }

        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private suspend fun getAuthAccessTokenData(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val jsonPayload = when (val result = subscriptionsManager.getAccessToken()) {
            is AccessTokenResult.Success -> JSONObject().apply {
                put(ACCESS_TOKEN, result.accessToken)
            }

            is AccessTokenResult.Failure -> JSONObject()
        }

        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    private suspend fun getFeatureConfigData(
        featureName: String,
        method: String,
        id: String,
    ): JsCallbackData {
        val jsonPayload = JSONObject().apply {
            put(USE_PAID_DUCK_AI, privacyProFeature.duckAiPlus().isEnabled())
        }

        return JsCallbackData(jsonPayload, featureName, method, id)
    }

    companion object {
        private const val METHOD_HANDSHAKE = "handshake"
        private const val METHOD_SUBSCRIPTION_DETAILS = "subscriptionDetails"
        private const val METHOD_GET_AUTH_ACCESS_TOKEN = "getAuthAccessToken"
        private const val METHOD_GET_FEATURE_CONFIG = "getFeatureConfig"
        private const val AVAILABLE_MESSAGES = "availableMessages"
        private const val SUBSCRIPTION_DETAILS = "subscriptionDetails"
        private const val GET_AUTH_ACCESS_TOKEN = "getAuthAccessToken"
        private const val AUTH_UPDATE = "authUpdate"
        private const val GET_FEATURE_CONFIG = "getFeatureConfig"
        private const val PLATFORM = "platform"
        private const val ANDROID = "android"
        private const val IS_SUBSCRIBED = "isSubscribed"
        private const val BILLING_PERIOD = "billingPeriod"
        private const val STARTED_AT = "startedAt"
        private const val EXPIRES_OR_RENEWS_AT = "expiresOrRenewsAt"
        private const val PAYMENT_PLATFORM = "paymentPlatform"
        private const val STATUS = "status"
        private const val ACCESS_TOKEN = "accessToken"
        private const val USE_PAID_DUCK_AI = "usePaidDuckAi"
    }
}
