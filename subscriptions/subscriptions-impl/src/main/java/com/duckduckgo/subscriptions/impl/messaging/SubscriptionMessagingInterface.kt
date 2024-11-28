/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.AuthTokenResult
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.json.JSONObject

@ContributesBinding(ActivityScope::class)
@Named("Subscriptions")
class SubscriptionMessagingInterface @Inject constructor(
    subscriptionsManager: SubscriptionsManager,
    private val jsMessageHelper: JsMessageHelper,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    pixelSender: SubscriptionPixelSender,
    subscriptionsChecker: SubscriptionsChecker,
) : JsMessaging {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView
    private lateinit var jsMessageCallback: JsMessageCallback

    private val handlers = listOf(
        SubscriptionsHandler(),
        GetSubscriptionMessage(subscriptionsManager, dispatcherProvider),
        SetSubscriptionMessage(subscriptionsManager, appCoroutineScope, dispatcherProvider, pixelSender, subscriptionsChecker),
        InformationalEventsMessage(subscriptionsManager, appCoroutineScope, pixelSender),
        GetAccessTokenMessage(subscriptionsManager),
    )

    @JavascriptInterface
    override fun process(message: String, secret: String) {
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)
            val url = runBlocking(dispatcherProvider.main()) {
                webView.url?.toUri()?.host
            }
            jsMessage?.let {
                logcat { jsMessage.toString() }
                if (this.secret == secret && context == jsMessage.context && isUrlAllowed(url)) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, secret, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
    }

    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, context)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )
        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
    }

    override fun onResponse(response: JsCallbackData) {
        val jsResponse = JsRequestResponse.Success(
            context = context,
            featureName = response.featureName,
            method = response.method,
            id = response.id,
            result = response.params,
        )

        jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, webView)
    }

    override val context: String = "subscriptionPages"
    override val callbackName: String = "messageCallback"
    override val secret: String = "duckduckgo-android-messaging-secret"
    override val allowedDomains: List<String> = listOf("duckduckgo.com")

    private fun isUrlAllowed(url: String?): Boolean {
        if (allowedDomains.isEmpty()) return true
        val eTld = url?.toTldPlusOne() ?: return false
        return (allowedDomains.contains(eTld))
    }

    inner class SubscriptionsHandler : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf(
            "subscriptionSelected",
            "getSubscriptionOptions",
            "backToSettings",
            "activateSubscription",
            "featureSelected",
            "backToSettingsActivateSuccess",
        )
    }

    inner class GetSubscriptionMessage(
        private val subscriptionsManager: SubscriptionsManager,
        private val dispatcherProvider: DispatcherProvider,
    ) : JsMessageHandler {

        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            if (jsMessage.id == null) return

            val authToken: String? = runBlocking(dispatcherProvider.io()) {
                val pat = subscriptionsManager.getAuthToken()
                when (pat) {
                    is AuthTokenResult.Success -> pat.authToken
                    is AuthTokenResult.Failure.TokenExpired -> pat.authToken
                    else -> null
                }
            }

            val data = if (authToken != null) {
                JsRequestResponse.Success(
                    context = jsMessage.context,
                    featureName = featureName,
                    method = jsMessage.method,
                    id = jsMessage.id!!,
                    result = JSONObject("""{ "token":"$authToken"}"""),
                )
            } else {
                JsRequestResponse.Success(
                    context = jsMessage.context,
                    featureName = featureName,
                    method = jsMessage.method,
                    id = jsMessage.id!!,
                    result = JSONObject("""{ }"""),
                )
            }

            jsMessageHelper.sendJsResponse(data, callbackName, secret, webView)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf("getSubscription")
    }

    inner class SetSubscriptionMessage(
        private val subscriptionsManager: SubscriptionsManager,
        @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
        private val dispatcherProvider: DispatcherProvider,
        private val pixelSender: SubscriptionPixelSender,
        private val subscriptionsChecker: SubscriptionsChecker,
    ) : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            try {
                val token = jsMessage.params.getString("token")
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    subscriptionsManager.exchangeAuthToken(token)
                    subscriptionsManager.fetchAndStoreAllData()
                    subscriptionsChecker.runChecker()
                    pixelSender.reportRestoreUsingEmailSuccess()
                    pixelSender.reportSubscriptionActivated()
                }
            } catch (e: Exception) {
                logcat { "Error parsing the token" }
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf("setSubscription")
    }

    private class InformationalEventsMessage(
        private val subscriptionsManager: SubscriptionsManager,
        @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
        private val pixelSender: SubscriptionPixelSender,
    ) : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            secret: String,
            jsMessageCallback: JsMessageCallback?,
        ) {
            appCoroutineScope.launch {
                when (jsMessage.method) {
                    "subscriptionsMonthlyPriceClicked" -> pixelSender.reportMonthlyPriceClick()
                    "subscriptionsYearlyPriceClicked" -> pixelSender.reportYearlyPriceClick()
                    "subscriptionsAddEmailSuccess" -> {
                        pixelSender.reportAddEmailSuccess()
                        subscriptionsManager.tryRefreshAccessToken()
                    }
                    "subscriptionsEditEmailSuccess" -> subscriptionsManager.tryRefreshAccessToken()
                    "subscriptionsWelcomeAddEmailClicked",
                    "subscriptionsWelcomeFaqClicked",
                    -> {
                        jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
                    }

                    else -> {} // no-op
                }
            }
        }

        private suspend fun SubscriptionsManager.tryRefreshAccessToken() {
            try {
                if (subscriptionsManager.isSignedInV2()) {
                    refreshAccessToken()
                }
            } catch (e: Exception) {
                logcat { e.stackTraceToString() }
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf(
            "subscriptionsMonthlyPriceClicked",
            "subscriptionsYearlyPriceClicked",
            "subscriptionsUnknownPriceClicked",
            "subscriptionsAddEmailSuccess",
            "subscriptionsEditEmailSuccess",
            "subscriptionsWelcomeAddEmailClicked",
            "subscriptionsWelcomeFaqClicked",
        )
    }

    private inner class GetAccessTokenMessage(
        private val subscriptionsManager: SubscriptionsManager,
    ) : JsMessageHandler {

        override fun process(
            jsMessage: JsMessage,
            secret: String,
            jsMessageCallback: JsMessageCallback?,
        ) {
            val jsMessageId = jsMessage.id ?: return

            val pat: AccessTokenResult = runBlocking {
                subscriptionsManager.getAccessToken()
            }

            val resultJson = when (pat) {
                is AccessTokenResult.Success -> """{"token":"${pat.accessToken}"}"""
                is AccessTokenResult.Failure -> """{ }"""
            }

            val response = JsRequestResponse.Success(
                context = jsMessage.context,
                featureName = featureName,
                method = jsMessage.method,
                id = jsMessageId,
                result = JSONObject(resultJson),
            )

            jsMessageHelper.sendJsResponse(response, callbackName, secret, webView)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val methods: List<String> = listOf("getAccessToken")
    }
}
