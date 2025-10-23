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

package com.duckduckgo.pir.impl.scripts

import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.impl.scripts.models.ActionRequest
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirResult
import com.duckduckgo.pir.impl.scripts.models.PirScriptError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestParams
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.ERROR
import logcat.logcat
import org.json.JSONObject

/**
 * This class is responsible for executing a broker action via js messaging
 */
interface BrokerActionProcessor {
    /**
     * Registers the pir JsMessagingInterface to the [webView]
     */
    fun register(
        webView: WebView,
        actionResultListener: ActionResultListener,
    )

    /**
     * Executes the [action] for the given [profileQuery] and/or [extractedProfile]
     */
    fun pushAction(
        action: BrokerAction,
        requestParamsData: PirScriptRequestData,
    )

    interface ActionResultListener {
        fun onSuccess(pirSuccessResponse: PirSuccessResponse)
        fun onError(pirError: PirError)
    }
}

class RealBrokerActionProcessor(
    private val pirMessagingInterface: JsMessaging,
) : BrokerActionProcessor {
    private val requestAdapter by lazy {
        Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(PirScriptRequestData::class.java, "data")
                    .withSubtype(SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(UserProfile::class.java, "userProfile"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerAction::class.java, "actionType")
                    .withSubtype(BrokerAction.Extract::class.java, "extract")
                    .withSubtype(BrokerAction.Expectation::class.java, "expectation")
                    .withSubtype(BrokerAction.Click::class.java, "click")
                    .withSubtype(BrokerAction.FillForm::class.java, "fillForm")
                    .withSubtype(BrokerAction.Navigate::class.java, "navigate")
                    .withSubtype(BrokerAction.GetCaptchaInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation")
                    .withSubtype(BrokerAction.Condition::class.java, "condition"),
            ).add(KotlinJsonAdapterFactory())
            .build()
            .adapter(PirScriptRequestParams::class.java)
    }
    private val responseAdapter by lazy {
        Moshi.Builder().add(
            PolymorphicJsonAdapterFactory.of(PirSuccessResponse::class.java, "actionType")
                .withSubtype(NavigateResponse::class.java, "navigate")
                .withSubtype(ExtractedResponse::class.java, "extract")
                .withSubtype(GetCaptchaInfoResponse::class.java, "getCaptchaInfo")
                .withSubtype(SolveCaptchaResponse::class.java, "solveCaptcha")
                .withSubtype(ClickResponse::class.java, "click")
                .withSubtype(ExpectationResponse::class.java, "expectation")
                .withSubtype(FillFormResponse::class.java, "fillForm"),
        ).add(KotlinJsonAdapterFactory())
            .build().adapter(PirResult::class.java)
    }

    private val errorAdapter by lazy {
        Moshi.Builder().build().adapter(PirScriptError::class.java)
    }

    private var registeredActionResultListener: ActionResultListener? = null

    override fun register(
        webView: WebView,
        actionResultListener: ActionResultListener,
    ) {
        registeredActionResultListener = actionResultListener
        pirMessagingInterface.register(
            webView,
            object : JsMessageCallback() {
                override fun process(
                    featureName: String,
                    method: String,
                    id: String?,
                    data: JSONObject?,
                ) {
                    processJsCallbackMessage(featureName, method, id, data)
                }
            },
        )
    }

    override fun pushAction(
        action: BrokerAction,
        requestParamsData: PirScriptRequestData,
    ) {
        logcat { "PIR-CSS: pushAction action: $action" }

        /**
         * 1. Transform action to ActionRequest
         * 2. Create action to SubscriptionEvent
         * 3. Send SubscriptionEvent for method onActionReceived
         */
        SubscriptionEventData(
            featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
            subscriptionName = PIRScriptConstants.SUBSCRIBED_METHOD_NAME_RECEIVED,
            params = requestAdapter.toJson(
                PirScriptRequestParams(
                    state = ActionRequest(
                        action = action,
                        data = requestParamsData,
                    ),
                ),
            ).run {
                JSONObject(this)
            },
        ).also {
            sendJsEvent(it)
        }
    }

    private fun handleError(pirError: PirError) {
        logcat { "PIR-CSS: handleError: $pirError" }
        registeredActionResultListener?.onError(pirError)
    }

    private fun handleSuccess(result: PirSuccessResponse) {
        logcat { "PIR-CSS: handleSuccess: $result for action" }
        registeredActionResultListener?.onSuccess(result)
    }

    private fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ) {
        logcat { "PIR-CSS: METHOD $method" }
        if (method == PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED && data != null) {
            val result = kotlin.runCatching {
                responseAdapter.fromJson(data.toString())
            }.onFailure {
                logcat(ERROR) { "PIR-CSS: Failed to parse JS callback message $it " }
            }.getOrNull()

            logcat { "PIR-CSS: JsCallback response $result " }
            if (result?.result?.success != null) {
                handleSuccess(result.result.success)
            } else if (result?.result?.error != null) {
                handleError(
                    PirError.ActionFailed(
                        actionID = result.result.error.actionID,
                        message = result.result.error.message,
                    ),
                )
            } else {
                handleError(PirError.JsError.ParsingErrorObjectFailed)
            }
        } else if (method == PIRScriptConstants.RECEIVED_METHOD_NAME_ERROR && data != null) {
            val result = kotlin.runCatching {
                errorAdapter.fromJson(data.toString())
            }.onFailure {
                logcat(ERROR) { "PIR-CSS: Failed to parse JS callback message $it " }
            }.getOrNull()

            if (result?.error == "No action found.") {
                handleError(PirError.JsError.NoActionFound)
            } else if (result != null) {
                handleError(PirError.JsError.Unknown(result.error))
            } else {
                handleError(PirError.JsError.ParsingErrorObjectFailed)
            }
        } else {
            handleError(PirError.JsError.ParsingErrorObjectFailed)
        }
    }

    private fun sendResponseToJs(data: JsCallbackData) {
        logcat { "PIR-CSS: sendResponseToJs: data $data " }
        pirMessagingInterface.onResponse(data)
    }

    private fun sendJsEvent(event: SubscriptionEventData) {
        logcat { "PIR-CSS: sendJsEvent: event $event " }
        pirMessagingInterface.sendSubscriptionEvent(event)
    }
}
