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

package com.duckduckgo.pir.internal.scripts

import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.internal.scripts.BrokerActionProcessor.ActionResultListener
import com.duckduckgo.pir.internal.scripts.models.ActionRequest
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirResult
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.SolveCaptcha
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestParams
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
     * Executes the [action] for the given [profileQuery]
     */
    fun pushAction(
        profileQuery: ProfileQuery,
        action: BrokerAction,
        webView: WebView,
    )

    interface ActionResultListener {
        fun onSuccess(pirSuccessResponse: PirSuccessResponse)
        fun onError(pirErrorReponse: PirErrorReponse)
    }
}

@ContributesBinding(ServiceScope::class)
class RealBrokerActionProcessor @Inject constructor(
    @Named("BrokerProtection") private val pirMessagingInterface: JsMessaging,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : BrokerActionProcessor {
    private val requestAdapter = Moshi.Builder()
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
                .withSubtype(BrokerAction.GetCaptchInfo::class.java, "getCaptchaInfo")
                .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation"),
        ).add(KotlinJsonAdapterFactory())
        .build()
        .adapter(PirScriptRequestParams::class.java)
    private val responseAdapter = Moshi.Builder().add(
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
    private var registeredActionResultListener: ActionResultListener? = null
    private var timerJob: Job? = null
    private var actionPushed: BrokerAction? = null
    private var resultReceived: Boolean = false

    override fun register(
        webView: WebView,
        actionResultListener: ActionResultListener,
    ) {
        actionPushed = null
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
        profileQuery: ProfileQuery,
        action: BrokerAction,
        webView: WebView,
    ) {
        logcat { "PIR-CSS: pushAction action: $action" }
        resultReceived = false
        timerJob?.cancel()
        timerJob = coroutineScope.launch(dispatcherProvider.io()) {
            logcat { "PIR-CSS: run timer for $action" }
            delay(70000) // 1 minute + 10 seconds
            logcat { "PIR-CSS: delay done" }
            if (!resultReceived) {
                logcat { "PIR-CSS: NO result for $action" }
                // TODO : Might be better to retry?
                handleError(
                    PirErrorReponse(
                        actionID = actionPushed?.id ?: "no-id",
                        message = "Local timeout",
                    ),
                )
                timerJob?.cancel()
            }
        }
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
                        data = UserProfile(
                            userProfile = profileQuery,
                        ),
                    ),
                ),
            ).run {
                JSONObject(this)
            },
        ).also {
            actionPushed = action
            sendJsEvent(it)
        }
    }

    private fun handleError(result: PirErrorReponse) {
        logcat { "PIR-CSS: handleError: $result for action: $actionPushed" }
        if (actionPushed?.id == result.actionID) {
            registeredActionResultListener?.onError(result)
        }
    }

    private fun handleSuccess(result: PirSuccessResponse) {
        logcat { "PIR-CSS: handleSuccess: $result for action: $actionPushed" }
        if (actionPushed?.id == result.actionID) {
            registeredActionResultListener?.onSuccess(result)
        }
    }

    private fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ) {
        if (method == PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED && data != null) {
            val result = kotlin.runCatching {
                responseAdapter.fromJson(data.toString())
            }.onFailure {
                logcat(ERROR) { "PIR-CSS: Failed to parse JS callback message $it " }
            }.getOrNull()
            resultReceived = true
            logcat { "PIR-CSS: JsCallback response $result " }
            if (result?.result?.success != null) {
                timerJob?.cancel()
                handleSuccess(result.result.success)
            } else if (result?.result?.error != null) {
                timerJob?.cancel()
                handleError(result.result.error)
            }
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
