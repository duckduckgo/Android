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
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.json.JSONObject

@ContributesBinding(ActivityScope::class)
@Named("Itr")
class ItrMessagingInterface @Inject constructor(
    subscriptionsManager: SubscriptionsManager,
    private val jsMessageHelper: JsMessageHelper,
    private val dispatcherProvider: DispatcherProvider,
) : JsMessaging {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView
    private var jsMessageCallback: JsMessageCallback? = null

    private val handlers = listOf(
        GetAccessTokenMessage(subscriptionsManager, dispatcherProvider),
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
                if (this.secret == secret && context == jsMessage.context && isUrlAllowed(url)) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, this, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
    }

    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, context)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        // NOOP
    }

    override fun onResponse(response: JsCallbackData) {
        // NOOP
    }

    private fun isUrlAllowed(url: String?): Boolean {
        if (allowedDomains.isEmpty()) return true
        val eTld = url?.toTldPlusOne() ?: return false
        return (allowedDomains.contains(eTld))
    }

    override val context: String = "identityTheftRestorationPages"
    override val callbackName: String = "messageCallback"
    override val secret: String = "duckduckgo-android-messaging-secret"
    override val allowedDomains: List<String> = listOf("duckduckgo.com")

    inner class GetAccessTokenMessage(
        private val subscriptionsManager: SubscriptionsManager,
        private val dispatcherProvider: DispatcherProvider,
    ) : JsMessageHandler {

        override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
            if (jsMessage.id == null) return

            val pat: AccessTokenResult = runBlocking(dispatcherProvider.io()) {
                subscriptionsManager.getAccessToken()
            }

            val data = when (pat) {
                is AccessTokenResult.Success -> {
                    JsRequestResponse.Success(
                        context = jsMessage.context,
                        featureName = featureName,
                        method = jsMessage.method,
                        id = jsMessage.id!!,
                        result = JSONObject("""{ "token":"${pat.accessToken}"}"""),
                    )
                }

                is AccessTokenResult.Failure -> {
                    JsRequestResponse.Success(
                        context = jsMessage.context,
                        featureName = featureName,
                        method = jsMessage.method,
                        id = jsMessage.id!!,
                        result = JSONObject("""{ }"""),
                    )
                }
            }
            jsMessageHelper.sendJsResponse(data, callbackName, secret, webView)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "useIdentityTheftRestoration"
        override val methods: List<String> = listOf("getAccessToken")
    }
}
