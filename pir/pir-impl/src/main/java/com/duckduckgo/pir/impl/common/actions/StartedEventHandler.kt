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
import com.duckduckgo.pir.impl.common.PirJobConstants.DBP_INITIAL_URL
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.Started
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.UUID
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class StartedEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = Started::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        return Next(
            nextState = state.copy(
                pendingUrl = DBP_INITIAL_URL,
                attemptId = UUID.randomUUID().toString(),
            ),
            sideEffect = LoadUrl(
                url = DBP_INITIAL_URL,
            ),
        )
    }
}
