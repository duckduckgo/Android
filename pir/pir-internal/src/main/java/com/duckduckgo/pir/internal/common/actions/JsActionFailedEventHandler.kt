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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirRunStateHandler
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutActionFailed
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScanActionFailed
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.BrokerActionsCompleted
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.JsActionFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.GetCaptchaInfo
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.internal.scripts.models.asActionType
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
        val currentBroker = state.brokers[state.currentBrokerIndex]
        val currentAction = currentBroker.actions[state.currentActionIndex]
        val error = (event as JsActionFailed).error

        if (state.runType != RunType.OPTOUT) {
            pirRunStateHandler.handleState(
                BrokerScanActionFailed(
                    brokerName = currentBroker.brokerName,
                    actionType = currentAction.asActionType(),
                    actionID = error.actionID,
                    message = error.message,
                ),
            )
        } else {
            state.extractedProfile[state.currentExtractedProfileIndex].let {
                pirRunStateHandler.handleState(
                    BrokerOptOutActionFailed(
                        brokerName = currentBroker.brokerName,
                        extractedProfile = it,
                        completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        actionType = currentAction.asActionType(),
                        actionID = error.actionID,
                        message = error.message,
                    ),
                )
            }
        }

        // If failure is on Any captcha action, we proceed to next action
        return if (currentAction is GetCaptchaInfo || currentAction is SolveCaptcha) {
            Next(
                nextState = state.copy(
                    currentActionIndex = state.currentActionIndex + 1,
                ),
                nextEvent = ExecuteNextBrokerAction(
                    UserProfile(
                        userProfile = state.profileQuery,
                    ),
                ),
            )
        } else {
            // If error happens we skip to next Broker as next steps will not make sense
            Next(
                nextState = state,
                nextEvent = BrokerActionsCompleted(isSuccess = false),
            )
        }
    }
}
