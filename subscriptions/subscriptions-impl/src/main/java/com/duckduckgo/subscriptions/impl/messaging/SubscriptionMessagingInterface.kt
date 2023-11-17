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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.subscriptions.impl.AuthToken
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
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
) : JsMessaging {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView
    private lateinit var jsMessageCallback: JsMessageCallback

    private val handlers = listOf(
        BackToSettingsMessage(),
        GetSubscriptionMessage(subscriptionsManager, dispatcherProvider),
        SetSubscriptionMessage(subscriptionsManager, appCoroutineScope, dispatcherProvider),
    )

    @JavascriptInterface
    override fun process(message: String, secret: String) {
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)
            val domain = runBlocking(dispatcherProvider.main()) {
                webView.url?.toUri()?.host
            }
            jsMessage?.let {
                if (this.secret == secret && context == jsMessage.context && allowedDomains.contains(domain)) {
                    handlers.firstOrNull {
                        it.method == jsMessage.method && it.featureName == jsMessage.featureName
                    }?.let {
                        val response = it.process(jsMessage, secret, webView, jsMessageCallback)
                        response?.let {
                            jsMessageHelper.sendJsResponse(response, callbackName, secret, webView)
                        }
                    }
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

    override fun sendSubscriptionEvent() {
        // NOOP
    }

    override fun onResponse(response: JsCallbackData) {
        // NOOP
    }

    override val context: String = "subscriptionPages"
    override val callbackName: String = "messageCallback"
    override val secret: String = "duckduckgo-android-messaging-secret"
    override val allowedDomains: List<String> = listOf("abrown.duckduckgo.com")

    inner class BackToSettingsMessage : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, webView: WebView, jsMessageCallback: JsMessageCallback): JsRequestResponse? {
            if (jsMessage.featureName != featureName && jsMessage.method != method) return null
            jsMessageCallback.process(featureName, method, jsMessage.id!!, jsMessage.params)
            return null
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val method: String = "backToSettings"
    }

    inner class GetSubscriptionMessage(
        private val subscriptionsManager: SubscriptionsManager,
        private val dispatcherProvider: DispatcherProvider,
    ) : JsMessageHandler {

        override fun process(jsMessage: JsMessage, secret: String, webView: WebView, jsMessageCallback: JsMessageCallback): JsRequestResponse? {
            if (jsMessage.featureName != featureName && jsMessage.method != method) return null
            if (jsMessage.id == null) return null

            val pat: AuthToken = runBlocking(dispatcherProvider.io()) {
                subscriptionsManager.getAuthToken()
            }

            return when (pat) {
                is AuthToken.Success -> {
                    JsRequestResponse.Success(
                        context = jsMessage.context,
                        featureName = featureName,
                        method = method,
                        id = jsMessage.id!!,
                        result = JSONObject("""{ "token":"${pat.authToken}"}"""),
                    )
                }

                is AuthToken.Failure -> {
                    JsRequestResponse.Success(
                        context = jsMessage.context,
                        featureName = featureName,
                        method = method,
                        id = jsMessage.id!!,
                        result = JSONObject("""{ }"""),
                    )
                }
            }
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val method: String = "getSubscription"
    }

    inner class SetSubscriptionMessage(
        private val subscriptionsManager: SubscriptionsManager,
        @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
        private val dispatcherProvider: DispatcherProvider,
    ) : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, webView: WebView, jsMessageCallback: JsMessageCallback): JsRequestResponse? {
            if (jsMessage.featureName != featureName && jsMessage.method != method) return null
            try {
                val token = jsMessage.params.getString("token")
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    subscriptionsManager.authenticate(token)
                }
            } catch (e: Exception) {
                logcat { "Error parsing the token" }
            }
            return null
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useSubscription"
        override val method: String = "setSubscription"
    }
}
