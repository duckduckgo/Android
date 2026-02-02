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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepInvalidEvent
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerActionFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ErrorReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class ErrorReceivedHandler @Inject constructor(
    private val pirRunStateHandler: PirRunStateHandler,
) : EventHandler {
    override val event: KClass<out Event> = ErrorReceived::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * An error occurred while the engine is running means that the current action executions then fails.
         * The following errors could be:
         *  - JsError (Unrecoverable)
         *  - CaptchaServiceError (Unrecoverable)
         *  - EmailError (Unrecoverable)
         *  - ClientError (Unrecoverable)
         *  We don't need to retry the action if any of these errors happen.
         */
        if (!isEventValid(state, event as ErrorReceived)) {
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

        return Next(
            nextState = state,
            nextEvent = BrokerActionFailed(
                error = (event as ErrorReceived).error,
                allowRetry = false,
            ),
        )
    }

    private fun isEventValid(
        state: State,
        event: ErrorReceived,
    ): Boolean {
        // Broker steps has probably been considered completed before the js response arrived
        if (state.brokerStepsToExecute.size <= state.currentBrokerStepIndex) return false

        // Broker step actions has probably been considered completed before the js response arrived
        if (state.brokerStepsToExecute[state.currentBrokerStepIndex].step.actions.size <= state.currentActionIndex) return false

        val currentBrokerStep = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val currentBrokerStepAction = currentBrokerStep.step.actions[state.currentActionIndex]

        // The action IDs don't match, the js response is probably for an outdated / old action
        if (event.error is PirError.ActionError && event.error.actionID != currentBrokerStepAction.id) return false

        return true
    }
}
