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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutConditionNotFound
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaParsed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageFillForm
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepInvalidEvent
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ConditionExpectationSucceeded
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.JsActionSuccess
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.EvaluateJs
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ConditionResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class JsActionSuccessEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = JsActionSuccess::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * This means we have received a success from the JS layer for the last action we pushed.
         * - NavigateResponse -> we load the resulting link to the webview
         * - GetCaptchaInfoResponse -> we need to execute a BE call to submit the captcha info to the BE and start the solution
         * - SolveCaptchaResponse -> we load the callback (js script) into the webview AND proceed to the next action.
         * - Else -> we proceed to the next action
         */
        if (!isEventValid(state, event as JsActionSuccess)) {
            // Nothing to do here, the event is outdated
            val broker = if (state.brokerStepsToExecute.size <= state.currentBrokerStepIndex) {
                Broker.unknown()
            } else {
                state.brokerStepsToExecute[state.currentBrokerStepIndex].broker
            }

            pirRunStateHandler.handleState(
                BrokerStepInvalidEvent(
                    broker = broker,
                    runType = state.runType,
                ),
            )
            return Next(nextState = state)
        }

        val pirSuccessResponse = event.pirSuccessResponse
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val baseSuccessState = state.copy(
            actionRetryCount = 0,
        )

        if (currentBrokerStep is OptOutStep) {
            pirRunStateHandler.handleState(
                BrokerOptOutActionSucceeded(
                    broker = currentBrokerStep.broker,
                    extractedProfile = currentBrokerStep.profileToOptOut,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    actionType = pirSuccessResponse.actionType,
                    result = pirSuccessResponse,
                ),
            )
        } else if (currentBrokerStep is EmailConfirmationStep) {
            pirRunStateHandler.handleState(
                BrokerOptOutActionSucceeded(
                    broker = currentBrokerStep.broker,
                    extractedProfile = currentBrokerStep.profileToOptOut,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    actionType = pirSuccessResponse.actionType,
                    result = pirSuccessResponse,
                ),
            )
        } else {
            pirRunStateHandler.handleState(
                BrokerScanActionSucceeded(
                    broker = currentBrokerStep.broker,
                    state.profileQuery.id,
                    pirSuccessResponse,
                ),
            )
        }

        attemptFireOptOutStagePixel(currentBrokerStep, pirSuccessResponse, state)

        return when (pirSuccessResponse) {
            is NavigateResponse -> {
                Next(
                    nextState = baseSuccessState.copy(
                        pendingUrl = pirSuccessResponse.response.url,
                    ),
                    sideEffect = LoadUrl(
                        url = pirSuccessResponse.response.url,
                    ),
                )
            }

            is FillFormResponse, is ClickResponse, is ExpectationResponse, is ExtractedResponse -> {
                Next(
                    nextState = baseSuccessState.copy(
                        currentActionIndex = baseSuccessState.currentActionIndex + 1,
                    ),
                    nextEvent = ExecuteBrokerStepAction(
                        UserProfile(
                            userProfile = baseSuccessState.profileQuery,
                        ),
                    ),
                )
            }

            is GetCaptchaInfoResponse -> {
                Next(
                    nextState = baseSuccessState.copy(
                        stageStatus = PirStageStatus(
                            currentStage = PirStage.CAPTCHA_SEND,
                            stageStartMs = currentTimeProvider.currentTimeMillis(),
                        ),
                    ),
                    sideEffect = GetCaptchaSolution(
                        actionId = pirSuccessResponse.actionID,
                        responseData = pirSuccessResponse.response,
                        isRetry = false,
                    ),
                )
            }

            is SolveCaptchaResponse -> {
                Next(
                    nextState = baseSuccessState.copy(
                        currentActionIndex = baseSuccessState.currentActionIndex + 1,
                    ),
                    sideEffect = EvaluateJs(
                        callback = pirSuccessResponse.response!!.callback.eval,
                    ),
                    nextEvent = ExecuteBrokerStepAction(
                        UserProfile(
                            userProfile = baseSuccessState.profileQuery,
                        ),
                    ),
                )
            }

            is ConditionResponse -> {
                if (pirSuccessResponse.response.actions.isNotEmpty()) {
                    Next(
                        nextState = baseSuccessState,
                        nextEvent = ConditionExpectationSucceeded(
                            pirSuccessResponse.response.actions,
                        ),
                    )
                } else {
                    Next(
                        nextState = baseSuccessState.copy(
                            currentActionIndex = baseSuccessState.currentActionIndex + 1,
                        ),
                        nextEvent = ExecuteBrokerStepAction(
                            UserProfile(
                                userProfile = baseSuccessState.profileQuery,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun isEventValid(
        state: State,
        jsActionSuccess: JsActionSuccess,
    ): Boolean {
        // Broker steps has probably been considered completed before the js response arrived
        if (state.brokerStepsToExecute.size <= state.currentBrokerStepIndex) return false

        // Broker step actions has probably been considered completed before the js response arrived
        if (state.brokerStepsToExecute[state.currentBrokerStepIndex].step.actions.size <= state.currentActionIndex) return false

        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val currentBrokerStepAction = currentBrokerStep.step.actions[state.currentActionIndex]

        // The action IDs don't match, the js response is probably for an outdated / old action
        if (currentBrokerStepAction.id != jsActionSuccess.pirSuccessResponse.actionID) return false

        return true
    }

    private suspend fun attemptFireOptOutStagePixel(
        currentBrokerStep: BrokerStep,
        response: PirSuccessResponse,
        state: State,
    ) {
        if (currentBrokerStep is OptOutStep) {
            if (response is GetCaptchaInfoResponse) {
                pirRunStateHandler.handleState(
                    BrokerOptOutStageCaptchaParsed(
                        broker = currentBrokerStep.broker,
                        actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                        attemptId = state.attemptId,
                        durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                        currentActionAttemptCount = state.actionRetryCount + 1,
                    ),
                )
            } else if (response is FillFormResponse || response is ClickResponse) {
                pirRunStateHandler.handleState(
                    BrokerOptOutStageFillForm(
                        broker = currentBrokerStep.broker,
                        actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                        attemptId = state.attemptId,
                        durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                        currentActionAttemptCount = state.actionRetryCount + 1,
                    ),
                )
            } else if (response is ConditionResponse && response.response.actions.isEmpty()) {
                pirRunStateHandler.handleState(
                    BrokerOptOutConditionNotFound(
                        broker = currentBrokerStep.broker,
                        actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                        attemptId = state.attemptId,
                        durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                        currentActionAttemptCount = state.actionRetryCount + 1,
                    ),
                )
            }
        }
    }
}
