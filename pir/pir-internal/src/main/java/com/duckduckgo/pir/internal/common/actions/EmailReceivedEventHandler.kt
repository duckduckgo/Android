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
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.actions.EventHandler.Next
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfileParams
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class EmailReceivedEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = EmailReceived::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * Once we have received the email address, we update the current extracted profile with the information.
         * We now re-do the last Broker action passing the updated extracted profile into the [State] and also the
         * [UserProfile] for the action.
         */
        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex] as OptOutStep

        val updatedProfileWithEmail = currentBrokerStep.profileToOptOut.copy(
            email = (event as EmailReceived).email,
        )

        val updatedBrokerSteps = state.brokerStepsToExecute.toMutableList().apply {
            this[state.currentBrokerStepIndex] = currentBrokerStep.copy(
                profileToOptOut = updatedProfileWithEmail,
            )
        }

        return Next(
            nextState = state.copy(
                brokerStepsToExecute = updatedBrokerSteps,
            ),
            nextEvent = ExecuteBrokerStepAction(
                actionRequestData = UserProfile(
                    userProfile = state.profileQuery,
                    extractedProfile = updatedProfileWithEmail.run {
                        ExtractedProfileParams(
                            name = this.name,
                            profileUrl = this.profileUrl,
                            fullName = state.profileQuery.fullName,
                            email = this.email,
                        )
                    },
                ),
            ),
        )
    }
}
