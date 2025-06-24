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
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.EmailFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.JsActionFailed
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.PirError
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class EmailFailedEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = EmailFailed::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * If we encounter an email related email, it means the broker action depending on it will fail.
         */
        val currentBroker = state.brokerStepsToExecute[state.currentBrokerStepIndex]
        val currentAction = currentBroker.actions[state.currentActionIndex]

        return Next(
            nextState = state,
            nextEvent = JsActionFailed(
                error = PirError.ActionFailed(
                    actionID = currentAction.id,
                    message = (event as EmailFailed).error.error,
                ),
            ),
        )
    }
}
