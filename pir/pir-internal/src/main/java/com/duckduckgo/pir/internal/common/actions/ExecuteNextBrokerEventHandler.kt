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
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirJob.RunType.MANUAL
import com.duckduckgo.pir.internal.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.internal.common.PirJob.RunType.SCHEDULED
import com.duckduckgo.pir.internal.common.PirRunStateHandler
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanStarted
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBroker
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ExecuteNextBrokerEventHandler @Inject constructor(
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRunStateHandler: PirRunStateHandler,
) : EventHandler {
    override val event: KClass<out Event> = ExecuteNextBroker::class

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
        return if (state.currentBrokerIndex >= state.brokers.size) {
            Next(
                nextState = state,
                sideEffect = CompleteExecution,
            )
        } else {
            // Entry point of execution for a Broker
            state.brokers[state.currentBrokerIndex].let {
                emitBrokerStartPixel(state.runType, it)

                val nextState = if (it is OptOutStep) {
                    state.copy(
                        currentActionIndex = 0,
                        brokerStartTime = currentTimeProvider.currentTimeMillis(),
                        currentExtractedProfileIndex = 0,
                        extractedProfile = it.profilesToOptOut,
                    )
                } else {
                    state.copy(
                        currentActionIndex = 0,
                        brokerStartTime = currentTimeProvider.currentTimeMillis(),
                    )
                }
                Next(
                    nextState = nextState,
                    nextEvent = ExecuteNextBrokerAction(
                        UserProfile(
                            userProfile = state.profileQuery,
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun emitBrokerStartPixel(
        runType: RunType,
        brokerStep: BrokerStep,
    ) {
        when (runType) {
            MANUAL -> pirRunStateHandler.handleState(
                BrokerManualScanStarted(
                    brokerStep.brokerName,
                    currentTimeProvider.currentTimeMillis(),
                ),
            )

            SCHEDULED -> pirRunStateHandler.handleState(
                BrokerScheduledScanStarted(
                    brokerStep.brokerName,
                    currentTimeProvider.currentTimeMillis(),
                ),
            )

            OPTOUT -> {
                // When we get here it means we are starting a new process for a new broker
                pirRunStateHandler.handleState(
                    BrokerOptOutStarted(
                        brokerStep.brokerName,
                    ),
                )

                // It also means we are starting it for the first profile. Succeeding profiles are handled in HandleNextProfileForBroker
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutStarted(
                        brokerStep.brokerName,
                        (brokerStep as OptOutStep).profilesToOptOut[0],
                    ),
                )
            }
        }
    }
}
