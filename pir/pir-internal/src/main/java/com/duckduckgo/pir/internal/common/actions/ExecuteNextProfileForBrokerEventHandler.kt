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
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirRunStateHandler
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStepAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextProfileForBroker
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ExecuteNextProfileForBrokerEventHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
) : EventHandler {
    override val event: KClass<out Event> = ExecuteNextProfileForBroker::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        // We reset action to 0 and update the profile state to the next profile
        val newModel = state.copy(
            currentActionIndex = 0,
            currentExtractedProfileIndex = state.currentExtractedProfileIndex + 1,
        )

        // Should only be run for opt out really
        if (state.runType == RunType.OPTOUT) {
            // Signal start for current run.
            pirRunStateHandler.handleState(
                BrokerRecordOptOutStarted(
                    brokerName = newModel.brokerStepsToExecute[newModel.currentBrokerStepIndex].brokerName,
                    extractedProfile = newModel.extractedProfile[newModel.currentExtractedProfileIndex],
                ),
            )
        }
        // Restart for broker but with different profile
        return Next(
            nextState = newModel,
            nextEvent = ExecuteNextBrokerStepAction(
                UserProfile(
                    userProfile = state.profileQuery,
                ),
            ),
        )
    }
}
