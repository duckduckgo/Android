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

package com.duckduckgo.pir.internal.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.internal.brokers.JSONObjectAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject

class PirWebMessagingInterface @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
) : JsMessaging {
    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).add(JSONObjectAdapter()).build() }
    private val handlers = listOf(
        BrokerProtectionMessageHandler(),
    )
    private lateinit var jsMessageCallback: JsMessageCallback
    private lateinit var webView: WebView

    override fun onResponse(response: JsCallbackData) {
        logcat { "PIR-WEB: response=$response" }

        val jsResponse = JsRequestResponse.Success(
            context = context,
            featureName = response.featureName,
            method = response.method,
            id = response.id,
            result = response.params,
        )

        jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, webView)
    }

    override fun register(
        webView: WebView,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: register 1" }
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, PIRWebUiConstants.SCRIPT_FEATURE_NAME)
        logcat { "PIR-WEB: register 2" }
    }

    @JavascriptInterface
    override fun process(
        message: String,
        secret: String,
    ) {
        logcat { "PIR-WEB: process message=$message, secret=$secret" }
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)

            when (jsMessage?.method) {
                "handshake" -> {
                    logcat { "PIR-WEB: Handshake received" }
                    jsMessageHelper.sendJsResponse(
                        JsRequestResponse.Success(
                            context = "dbpui", // PIRWebUiConstants.SCRIPT_CONTEXT_NAME,
                            featureName = "dbpuiCommunication",
                            method = "handshake",
                            id = jsMessage.id ?: "",
                            result = JSONObject().apply {
                                put("success", true)
                                put("version", jsMessage.params?.getInt("version") ?: 10)
                                putOpt(
                                    "userData",
                                    JSONObject().apply {
                                        put("isAuthenticatedUser", "true")
                                    },
                                )
                            /* put("userData", JSONObject().apply {)
                                 put("isAuthenticatedUser", "true")
                             }.toString())*/
                            },
                        ),
                        callbackName, secret, webView,
                    )
                }
                "getCurrentUserProfile" -> {
                    logcat { "PIR-WEB: getCurrentUserProfile received" }
                    jsMessageHelper.sendJsResponse(
                        JsRequestResponse.Success(
                            context = "dbpui", // PIRWebUiConstants.SCRIPT_CONTEXT_NAME,
                            featureName = "dbpuiCommunication",
                            method = "getCurrentUserProfile",
                            id = jsMessage.id ?: "",
                            result = JSONObject().apply {
                                put("success", false)
                                put("version", 10) // jsMessage.params.getInt("version") ?: 10)
                            },
                        ),
                        callbackName, secret, webView,
                    )
                }
                "initialScanStatus" -> {
                    logcat { "PIR-WEB: Initial scan status received" }
                    jsMessageHelper.sendJsResponse(
                        JsRequestResponse.Success(
                            context = "dbpui", // PIRWebUiConstants.SCRIPT_CONTEXT_NAME,
                            featureName = "dbpuiCommunication",
                            method = "initialScanStatus",
                            id = jsMessage.id ?: "",
                            result = JSONObject().apply {
                                put("success", false)
                                put("version", 10) // jsMessage.params.getInt("version") ?: 10)
                                putOpt("resultsFound", JSONArray())
                                putOpt("scanProgress", JSONArray())
                            },
                        ),
                        callbackName, secret, webView,
                    )
                }
                else -> {
                    logcat { "PIR-WEB: Unknown method ${jsMessage?.method}" }
                    jsMessageCallback.process(
                        featureName = jsMessage?.featureName ?: PIRWebUiConstants.SCRIPT_FEATURE_NAME,
                        method = jsMessage?.method ?: "",
                        id = jsMessage?.id ?: "",
                        data = jsMessage?.params ?: JSONObject(),
                    )
                }
            }

            /*jsMessage?.let {
                logcat { jsMessage.toString() }
                if (this.secret == secret && context == jsMessage.context) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, secret, jsMessageCallback)
                }
            }*/
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        logcat { "PIR-WEB: sendSubscriptionEvent data=$subscriptionEventData" }

        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )

        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
    }

    override val context: String = PIRWebUiConstants.SCRIPT_CONTEXT_NAME
    override val callbackName: String = PIRWebUiConstants.MESSAGE_CALLBACK // "dbpui" // "messageCallback"
    override val secret: String = PIRWebUiConstants.SECRET
    override val allowedDomains: List<String> = listOf(/*"duckduckgo.com"*/)

    // override val context: String = "subscriptionPages"
    // override val callbackName: String = "messageCallback"
    // override val secret: String = "duckduckgo-android-messaging-secret"
    // override val allowedDomains: List<String> = listOf("duckduckgo.com")

    inner class BrokerProtectionMessageHandler() : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            secret: String,
            jsMessageCallback: JsMessageCallback?,
        ) {
            logcat { "PIR-WEB: BrokerProtectionMessageHandler: process $jsMessage" }
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = listOf(/*"duckduckgo.com"*/)
        override val featureName: String = PIRWebUiConstants.SCRIPT_FEATURE_NAME
        override val methods: List<String> = listOf(
            "handshake",
            "getFeatureConfig",
            "openSendFeedbackModal",
            "saveProfile",
            "getCurrentUserProfile",
            "addNameToCurrentUserProfile",
            "deleteUserProfileData",
            "removeNameAtIndexFromCurrentUserProfile",
            "setNameAtIndexInCurrentUserProfile",
            "setBirthYearForCurrentUserProfile",
            "addAddressToCurrentUserProfile",
            "removeAddressAtIndexFromCurrentUserProfile",
            "setAddressAtIndexInCurrentUserProfile",
            "startScanAndOptOut",
            "scanAndOptOutStatusChanged",
            "initialScanStatus",
            "maintenanceScanStatus",
            "getDataBrokers",
            "getBackgroundAgentMetadata",
            "getVpnExclusionSetting",
            "setVpnExclusionSetting",
            "removeOptOutFromDashboard",
        )
    }
}
