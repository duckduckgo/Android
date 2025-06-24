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
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirRunStateHandler
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanCompleted
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class BrokerStepCompletedEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
    private val currentTimeProvider: CurrentTimeProvider,
) : EventHandler {
    override val event: KClass<out Event> = BrokerStepCompleted::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * Scan logic:
         *  - No concept of extracted profiles (extractedProfile is going to be empty)
         *  - This event means that we can complete the scan for the broker.
         *  - We emit the complete pixel and then execute the next broker.
         *
         * Opt out logic:
         * - If there are more extracted profile for the broker:
         *  - We emit the BrokerRecordOptOutCompleted pixel.
         *  - Proceed to the next extracted profile for the broker
         * - If we are at the last extracted profile, We emit the opt out complete pixel for the current broker
         *      and then execute the next broker.
         */

        // Now we emit pixels related to the Broker step
        emitBrokerStepCompletePixel(
            state = state,
            totalTimeMillis = currentTimeProvider.currentTimeMillis() - state.brokerStepStartTime,
            isSuccess = (event as BrokerStepCompleted).isSuccess,
        )

        return Next(
            nextState = state.copy(
                currentBrokerStepIndex = state.currentBrokerStepIndex + 1,
            ),
            nextEvent = ExecuteNextBrokerStep,
        )
    }

    private suspend fun emitBrokerStepCompletePixel(
        state: State,
        totalTimeMillis: Long,
        isSuccess: Boolean,
    ) {
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val brokerStartTime = state.brokerStepStartTime
        when (state.runType) {
            RunType.MANUAL ->
                pirRunStateHandler.handleState(
                    BrokerManualScanCompleted(
                        brokerName = currentBrokerStep.brokerName,
                        eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        totalTimeMillis = totalTimeMillis,
                        isSuccess = isSuccess,
                        startTimeInMillis = brokerStartTime,
                    ),
                )

            RunType.SCHEDULED -> pirRunStateHandler.handleState(
                BrokerScheduledScanCompleted(
                    brokerName = currentBrokerStep.brokerName,
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    totalTimeMillis = totalTimeMillis,
                    isSuccess = isSuccess,
                    startTimeInMillis = brokerStartTime,
                ),
            )

            RunType.OPTOUT -> {
                val currentOptOutStep = currentBrokerStep as OptOutStep
                pirRunStateHandler.handleState(
                    BrokerRecordOptOutCompleted(
                        brokerName = currentOptOutStep.brokerName,
                        extractedProfile = currentOptOutStep.profileToOptOut,
                        startTimeInMillis = state.brokerStepStartTime,
                        endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                        isSubmitSuccess = isSuccess,
                    ),
                )
            }
        }
    }
}
