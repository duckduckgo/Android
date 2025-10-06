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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirJob.RunType.EMAIL_CONFIRMATION
import com.duckduckgo.pir.impl.common.PirJob.RunType.MANUAL
import com.duckduckgo.pir.impl.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.impl.common.PirJob.RunType.SCHEDULED
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerManualScanStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScheduledScanStarted
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ExecuteNextBrokerStepEventHandler @Inject constructor(
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRunStateHandler: PirRunStateHandler,
) : EventHandler {
    override val event: KClass<out Event> = ExecuteNextBrokerStep::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * - If we have executed ALL brokers, the engine is completed!
         * - If there are still more brokers to execute,
         *      - Scan: we reset action index to 0.
         *      - Opt out:
         *          - we reset action index AND extracted profile index to 0.
         *          - We also update the [State] to reference to the extracted profiles for the next broker.
         */
        return if (state.currentBrokerStepIndex >= state.brokerStepsToExecute.size) {
            Next(
                nextState = state,
                sideEffect = CompleteExecution,
            )
        } else {
            // Entry point of execution for a Broker
            emitBrokerStartPixel(state)

            Next(
                nextState =
                state.copy(
                    currentActionIndex = 0,
                    brokerStepStartTime = currentTimeProvider.currentTimeMillis(),
                    actionRetryCount = 0,
                ),
                nextEvent =
                ExecuteBrokerStepAction(
                    UserProfile(
                        userProfile = state.profileQuery,
                    ),
                ),
            )
        }
    }

    private suspend fun emitBrokerStartPixel(state: State) {
        val runType = state.runType
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]

        when (runType) {
            MANUAL ->
                pirRunStateHandler.handleState(
                    BrokerManualScanStarted(
                        currentBrokerStep.brokerName,
                        currentTimeProvider.currentTimeMillis(),
                    ),
                )

            SCHEDULED ->
                pirRunStateHandler.handleState(
                    BrokerScheduledScanStarted(
                        currentBrokerStep.brokerName,
                        currentTimeProvider.currentTimeMillis(),
                    ),
                )

            OPTOUT -> {
                // It also means we are starting it for the first profile. Succeeding profiles are handled in HandleNextProfileForBroker
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutStarted(
                        currentBrokerStep.brokerName,
                        (currentBrokerStep as OptOutStep).profileToOptOut,
                    ),
                )
            }

            EMAIL_CONFIRMATION -> {
                pirRunStateHandler.handleState(
                    BrokerRecordEmailConfirmationStarted(
                        brokerName = currentBrokerStep.brokerName,
                        emailConfirmationJobRecord = (currentBrokerStep as EmailConfirmationStep).emailConfirmationJob,
                    ),
                )
            }
            else -> {
                // No-op
            }
        }
    }
}
