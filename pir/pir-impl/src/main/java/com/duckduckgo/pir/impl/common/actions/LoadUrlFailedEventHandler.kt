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
import com.duckduckgo.pir.impl.common.PirJobConstants.RECOVERY_URL
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class LoadUrlFailedEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = LoadUrlFailed::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * This is our attempt to recover since likely the webview and js loaded into it is unusable.
         * We need to load any url successfully to run the js again.
         */
        val actualEvent = event as LoadUrlFailed

        if (state.pendingUrl == null) {
            return Next(state)
        }

        if (actualEvent.url == RECOVERY_URL) {
            return Next(
                nextState = state.copy(
                    pendingUrl = null,
                ),
                nextEvent = BrokerStepCompleted(needsEmailConfirmation = false, isSuccess = false),
            )
        }

        return Next(
            nextState = state.copy(
                pendingUrl = RECOVERY_URL,
            ),
            sideEffect = LoadUrl(
                RECOVERY_URL,
            ),
        )
    }
}
