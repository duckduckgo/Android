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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<EventHandler>(),
)
class PreseedCookiesEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = Event.PreSeedCookies::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        val currentBrokerStep = state.brokerStep

        if (currentBrokerStep == null) {
            return Next(state)
        } else {
            val url = currentBrokerStep.broker.url
            return Next(
                nextState = state.copy(
                    pendingUrl = url,
                    preseeding = true,
                ),
                sideEffect = LoadUrl(url),
            )
        }
    }
}
