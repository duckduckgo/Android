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
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.CaptchaInfoReceived
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStepAction
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.internal.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class CaptchaInforReceivedEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = CaptchaInfoReceived::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * This event means we have obtained the transactionID from the backend using the info we have received
         * from the GetCaptchaInfo js action.
         * We should proceed to the next broker action.
         */
        return Next(
            nextState = state.copy(
                currentActionIndex = state.currentActionIndex + 1,
                transactionID = (event as CaptchaInfoReceived).transactionID,
            ),
            nextEvent = ExecuteNextBrokerStepAction(
                UserProfile(
                    userProfile = state.profileQuery,
                ),
            ),
        )
    }
}
