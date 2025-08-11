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

package com.duckduckgo.pir.impl.dashboard.messaging

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.brokers.JSONObjectAdapter
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirWebJsMessageHandler
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.runBlocking
import logcat.logcat

@ContributesBinding(ActivityScope::class)
@Named("PirDashboardWebMessaging")
class PirDashboardWebMessagingInterface @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
    private val dispatcherProvider: DispatcherProvider,
    private val messageHandlers: PluginPoint<PirWebJsMessageHandler>,
) : JsMessaging {

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).add(JSONObjectAdapter()).build()
    }
    private lateinit var jsMessageCallback: JsMessageCallback
    private lateinit var webView: WebView

    override val context: String = PirDashboardWebConstants.SCRIPT_CONTEXT_NAME
    override val callbackName: String = PirDashboardWebConstants.MESSAGE_CALLBACK
    override val secret: String = PirDashboardWebConstants.SECRET
    override val allowedDomains: List<String> = listOf(PirDashboardWebConstants.ALLOWED_DOMAIN)

    override fun register(
        webView: WebView,
        jsMessageCallback: JsMessageCallback?,
    ) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, context)
    }

    @JavascriptInterface
    override fun process(
        message: String,
        secret: String,
    ) {
        logcat { "PIR-WEB: process message=$message" }

        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)
            val url = runBlocking(dispatcherProvider.main()) {
                webView.url?.toUri()?.host
            }

            jsMessage?.let {
                if (this.secret == secret && context == jsMessage.context && isUrlAllowed(url)) {
                    // delegate processing to other handlers
                    messageHandlers.getPlugins().firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, this, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
    }

    override fun onResponse(response: JsCallbackData) {
        logcat { "PIR-WEB: onResponse response=$response" }

        val jsResponse = JsRequestResponse.Success(
            context = context,
            featureName = response.featureName,
            method = response.method,
            id = response.id,
            result = response.params,
        )

        jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, webView)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        logcat { "PIR-WEB: sendSubscriptionEvent subscriptionEventData=$subscriptionEventData" }

        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )

        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
    }

    private fun isUrlAllowed(url: String?): Boolean {
        if (allowedDomains.isEmpty()) return true
        val eTld = url?.toTldPlusOne() ?: return false
        return (allowedDomains.contains(eTld))
    }
}
