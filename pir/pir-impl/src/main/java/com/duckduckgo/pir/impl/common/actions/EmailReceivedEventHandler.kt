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
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageGenerateEmailReceived
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.common.toParams
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.GenerateEmail
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class EmailReceivedEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = EmailReceived::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val emailReceived = event as EmailReceived
        val currentAction = currentBrokerStep.step.actions[state.currentActionIndex]

        attemptFireOptOutStagePixel(currentBrokerStep, state)

        return if (currentAction is GenerateEmail) {
            // Explicit generateEmail flow: advance to the next action
            Next(
                nextState = state.copy(
                    currentActionIndex = state.currentActionIndex + 1,
                    generatedEmailData = emailReceived.generatedEmailData,
                ),
                nextEvent = ExecuteBrokerStepAction(
                    actionRequestData = UserProfile(
                        userProfile = state.profileQuery,
                    ),
                ),
            )
        } else {
            // Implicit needsEmail flow (FillForm): re-execute the same action with email.
            // Only OptOutStep and EmailConfirmationStep reach this path; ScanStep uses the explicit GenerateEmail flow above.
            val profileToOptOut = when (currentBrokerStep) {
                is OptOutStep -> currentBrokerStep.profileToOptOut
                is EmailConfirmationStep -> currentBrokerStep.profileToOptOut
                is BrokerStep.ScanStep -> {
                    return Next(
                        nextState = state,
                        nextEvent = BrokerStepCompleted(
                            needsEmailConfirmation = false,
                            stepStatus = StepStatus.Failure(error = PirError.Unknown("Trying to use decoupled email flow in ScanStep!")),
                        ),
                    )
                }
            }
            val extractedProfileParams = profileToOptOut.toParams(state.profileQuery.fullName).copy(
                email = emailReceived.generatedEmailData.emailAddress,
            )

            Next(
                nextState = state.copy(
                    generatedEmailData = emailReceived.generatedEmailData,
                ),
                nextEvent = ExecuteBrokerStepAction(
                    actionRequestData = UserProfile(
                        userProfile = state.profileQuery,
                        extractedProfile = extractedProfileParams,
                    ),
                ),
            )
        }
    }

    private suspend fun attemptFireOptOutStagePixel(
        currentBrokerStep: BrokerStep,
        state: State,
    ) {
        if (currentBrokerStep is OptOutStep) {
            pirRunStateHandler.handleState(
                BrokerOptOutStageGenerateEmailReceived(
                    broker = currentBrokerStep.broker,
                    actionID = currentBrokerStep.step.actions[state.currentActionIndex].id,
                    attemptId = state.attemptId,
                    durationMs = currentTimeProvider.currentTimeMillis() - state.stageStatus.stageStartMs,
                    currentActionAttemptCount = state.actionRetryCount + 1, // retry count starts at 0.
                ),
            )
        }
    }
}
