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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionFailed
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.JsActionFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.GetCaptchaInfo
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.asActionType
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class JsActionFailedEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = JsActionFailed::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * This means we have received an error from the JS layer for the last action we pushed.
         * We end the run for the broker.
         */
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val currentAction = currentBrokerStep.actions[state.currentActionIndex]
        val error = (event as JsActionFailed).error

        if (currentBrokerStep is OptOutStep) {
            pirRunStateHandler.handleState(
                BrokerOptOutActionFailed(
                    brokerName = currentBrokerStep.brokerName,
                    extractedProfile = currentBrokerStep.profileToOptOut,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    actionType = currentAction.asActionType(),
                    actionID = error.actionID,
                    message = error.message,
                ),
            )
        } else {
            pirRunStateHandler.handleState(
                BrokerScanActionFailed(
                    brokerName = currentBrokerStep.brokerName,
                    actionType = currentAction.asActionType(),
                    actionID = error.actionID,
                    message = error.message,
                ),
            )
        }

        // If failure is on Any captcha action, we proceed to next action
        return if (shouldRetryFailedAction(state, event, currentAction)) {
            Next(
                nextState = state.copy(
                    currentActionIndex = state.currentActionIndex,
                    actionRetryCount = state.actionRetryCount + 1,
                ),
                nextEvent = ExecuteBrokerStepAction(
                    UserProfile(
                        userProfile = state.profileQuery,
                    ),
                ),
            )
        } else if (currentAction is GetCaptchaInfo || currentAction is SolveCaptcha) {
            Next(
                nextState = state.copy(
                    currentActionIndex = state.currentActionIndex + 1,
                    actionRetryCount = 0,
                ),
                nextEvent = ExecuteBrokerStepAction(
                    UserProfile(
                        userProfile = state.profileQuery,
                    ),
                ),
            )
        } else {
            // If error happens we skip to next Broker as next steps will not make sense
            Next(
                nextState = state,
                nextEvent = BrokerStepCompleted(isSuccess = false),
            )
        }
    }

    private fun shouldRetryFailedAction(
        state: State,
        event: JsActionFailed,
        currentAction: BrokerAction,
    ): Boolean {
        if (!event.allowRetry) {
            return false
        }

        if (state.runType == RunType.OPTOUT) {
            // for optout, for ANY action we retry at most 3 times
            return state.actionRetryCount < MAX_RETRY_COUNT_OPTOUT
        } else {
            // For scans, we ONLY retry once if the action is expectation
            return (currentAction is Expectation && state.actionRetryCount < MAX_RETRY_COUNT_SCAN)
        }
    }

    companion object {
        const val MAX_RETRY_COUNT_OPTOUT = 3
        const val MAX_RETRY_COUNT_SCAN = 1
    }
}
