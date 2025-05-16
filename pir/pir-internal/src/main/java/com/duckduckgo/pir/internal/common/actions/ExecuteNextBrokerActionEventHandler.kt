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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.BrokerActionsCompleted
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitEmailConfirmation
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.GetEmailForProfile
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.PushJsAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.Click
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.EmailConfirmation
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.internal.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.internal.scripts.models.DataSource.EXTRACTED_PROFILE
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfileParams
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ExecuteNextBrokerActionEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = ExecuteNextBrokerAction::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * If we have executed ALL actions for the broker, we have successfylly completed the run for the broker.
         * If there are remaining actions:
         *  - If the action needs an email from the profile and no email is present, we get one [GetEmailForProfile]
         *  - If the action is a click or expectation, we need to add a short delay.
         *  - If the action is EmailConfirmation, we need to execute a BE request to do it via [AwaitEmailConfirmation]
         *  - If the action is SolveCaptcha AND actionRequestData is NOT [PirScriptRequestData.SolveCaptcha] (no solution yet),
         *      we execute a BE request via [AwaitCaptchaSolution]
         *  - If the action is SolveCaptcha AND actionRequestData is already [PirScriptRequestData.SolveCaptcha],
         *      we are ready to push the action to js layer
         *  - For any other action, we push it to the js layer via [PushJsAction]
         */
        val currentBroker = state.brokers[state.currentBrokerIndex]
        val requestData = (event as ExecuteNextBrokerAction).actionRequestData

        return if (state.currentActionIndex == currentBroker.actions.size) {
            Next(
                nextState = state,
                nextEvent = BrokerActionsCompleted(true),
            )
        } else {
            val actionToExecute = currentBroker.actions[state.currentActionIndex]

            if (actionToExecute.needsEmail && !hasEmail(state)) {
                Next(
                    nextState = state,
                    sideEffect = GetEmailForProfile(
                        actionId = actionToExecute.id,
                        brokerName = currentBroker.brokerName,
                        extractedProfile = state.extractedProfile[state.currentExtractedProfileIndex],
                        profileQuery = state.profileQuery,
                    ),
                )
            } else {
                var pushDelay = 0L
                // Adding a delay here similar to macOS - to ensure the site completes loading before executing anything.
                if (actionToExecute is Click || actionToExecute is Expectation) {
                    pushDelay = 10_000
                }

                if (actionToExecute is EmailConfirmation) {
                    Next(
                        nextState = state,
                        sideEffect = AwaitEmailConfirmation(
                            actionId = actionToExecute.id,
                            brokerName = currentBroker.brokerName,
                            extractedProfile = state.extractedProfile[state.currentExtractedProfileIndex],
                            pollingIntervalSeconds = actionToExecute.pollingTime.toFloat(),
                        ),
                    )
                } else if (actionToExecute is SolveCaptcha && requestData !is PirScriptRequestData.SolveCaptcha) {
                    Next(
                        nextState = state,
                        sideEffect = AwaitCaptchaSolution(
                            actionId = actionToExecute.id,
                            brokerName = currentBroker.brokerName,
                            transactionID = state.transactionID,
                            attempt = 0,
                        ),
                    )
                } else {
                    Next(
                        nextState = state,
                        sideEffect = PushJsAction(
                            actionToExecute.id,
                            actionToExecute,
                            pushDelay,
                            completeRequestData(state, actionToExecute, requestData),
                        ),
                    )
                }
            }
        }
    }

    private fun hasEmail(state: State): Boolean {
        return state.extractedProfile[state.currentExtractedProfileIndex].email != null
    }

    private fun completeRequestData(
        state: State,
        actionToExecute: BrokerAction,
        requestData: PirScriptRequestData,
    ): PirScriptRequestData {
        return if (actionToExecute.dataSource == EXTRACTED_PROFILE &&
            (requestData as UserProfile).extractedProfile == null
        ) {
            val extractedProfile = state.extractedProfile[state.currentExtractedProfileIndex]

            UserProfile(
                userProfile = requestData.userProfile,
                extractedProfile = extractedProfile.run {
                    ExtractedProfileParams(
                        name = this.name,
                        profileUrl = this.profileUrl?.profileUrl,
                        fullName = state.profileQuery?.fullName,
                        email = this.email,
                    )
                },
            )
        } else {
            requestData
        }
    }
}
