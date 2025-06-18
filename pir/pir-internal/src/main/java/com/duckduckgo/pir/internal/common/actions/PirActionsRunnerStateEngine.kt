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

package com.duckduckgo.pir.internal.common.actions

import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfile
import com.duckduckgo.pir.internal.scripts.models.PirError
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse.ResponseData
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import kotlinx.coroutines.flow.Flow

interface PirActionsRunnerStateEngine {

    /**
     * Flow that emits effects that should be handled outside of this engine
     */
    val sideEffect: Flow<SideEffect>

    /**
     * This method dispatches new events to the engine.
     * By default, this happens in the IO dispatcher
     */
    fun dispatch(event: Event)

    /**
     * Model representing the running state of the engine
     */
    data class State(
        val runType: RunType,
        val brokerStepsToExecute: List<BrokerStep>,
        val currentBrokerStepIndex: Int = 0,
        val currentActionIndex: Int = 0,
        val brokerStepStartTime: Long = -1L,
        val profileQuery: ProfileQuery? = null,
        val transactionID: String = "",
        val pendingUrl: String? = null,
    )

    /**
     * This model represents the events that the engine can handle and update the running state accordingly.
     */
    sealed class Event {
        data object Idle : Event()
        data class Started(
            val profileQuery: ProfileQuery,
        ) : Event()

        data class LoadUrlComplete(
            val url: String,
        ) : Event()

        data class LoadUrlFailed(
            val url: String,
        ) : Event()

        data class EmailFailed(
            val error: PirError.EmailError,
        ) : Event()

        data class EmailReceived(
            val email: String,
        ) : Event()

        data class EmailConfirmationLinkReceived(
            val confirmationLink: String,
        ) : Event()

        data object ExecuteNextBrokerStep : Event()

        data class ExecuteBrokerStepAction(
            val actionRequestData: PirScriptRequestData,
        ) : Event()

        data class BrokerStepCompleted(val isSuccess: Boolean) : Event()

        data class JsErrorReceived(
            val error: PirError.JsError,
        ) : Event()

        data class JsActionSuccess(
            val pirSuccessResponse: PirSuccessResponse,
        ) : Event()

        data class JsActionFailed(
            val error: PirError.ActionFailed,
        ) : Event()

        data class CaptchaServiceFailed(
            val error: PirError.CaptchaServiceError,
        ) : Event()

        data class RetryAwaitCaptchaSolution(
            val actionId: String,
            val brokerName: String,
            val transactionID: String,
            val attempt: Int = 0,
        ) : Event()

        data class CaptchaInfoReceived(
            val transactionID: String,
        ) : Event()

        data class RetryGetCaptchaSolution(
            val actionId: String,
            val responseData: ResponseData?,
        ) : Event()

        data class RetryGetEmailConfirmation(
            val actionId: String,
            val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val pollingIntervalSeconds: Float,
            val attempt: Int = 0,
        ) : Event()
    }

    /**
     * This model represents effects that should happen outside the engine.
     * Any work that could take more than a second to evaluate or requires to be on the main thread shouuld be a side effect.
     */
    sealed class SideEffect {
        data object None : SideEffect()
        data object CompleteExecution : SideEffect()
        data class PushJsAction(
            override val actionId: String,
            val action: BrokerAction,
            val pushDelay: Long = 0L,
            val requestParamsData: PirScriptRequestData,
        ) : SideEffect(), BrokerActionSideEffect

        data class GetEmailForProfile(
            override val actionId: String,
            val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val profileQuery: ProfileQuery?,
        ) : SideEffect(), BrokerActionSideEffect

        data class GetCaptchaSolution(
            override val actionId: String,
            val responseData: ResponseData?,
            val isRetry: Boolean,
        ) : SideEffect(), BrokerActionSideEffect

        data class AwaitEmailConfirmation(
            override val actionId: String,
            val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val pollingIntervalSeconds: Float,
            val retries: Int = 10,
            val attempt: Int = 0,
        ) : SideEffect(), BrokerActionSideEffect

        data class AwaitCaptchaSolution(
            override val actionId: String,
            val brokerName: String,
            val transactionID: String,
            val pollingIntervalSeconds: Int = 5,
            val retries: Int = 50,
            val attempt: Int = 0,
        ) : SideEffect(), BrokerActionSideEffect

        data class LoadUrl(
            val url: String,
        ) : SideEffect()

        data class EvaluateJs(
            val callback: String,
        ) : SideEffect()
    }

    interface BrokerActionSideEffect {
        val actionId: String
    }
}
