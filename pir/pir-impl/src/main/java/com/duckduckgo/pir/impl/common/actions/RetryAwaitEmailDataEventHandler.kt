/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class RetryAwaitEmailDataEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = RetryAwaitEmailData::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        val actualEvent = event as RetryAwaitEmailData
        return Next(
            nextState = state,
            sideEffect = AwaitEmailData(
                actionId = actualEvent.actionId,
                brokerName = actualEvent.brokerName,
                emailAddress = actualEvent.emailAddress,
                attemptId = actualEvent.attemptId,
                extractFields = actualEvent.extractFields,
                pollingIntervalSeconds = actualEvent.pollingIntervalSeconds,
                maxTimeoutSeconds = actualEvent.maxTimeoutSeconds,
                attempt = actualEvent.attempt + 1,
            ),
        )
    }
}
