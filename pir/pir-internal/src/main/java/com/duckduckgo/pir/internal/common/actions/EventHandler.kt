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

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import kotlin.reflect.KClass

interface EventHandler {
    /**
     * This represents the event that this eventHandler can handle.
     */
    val event: KClass<out Event>

    /**
     * This function is called when the [event] can be handled by the eventHandler.
     *
     * @param state [State] of the engine at the time the event was invoked.
     * @param event [Event] that the handler needs to handle.
     * @return returns the resulting [Next] class which should be used by the engine to update its state or forward next [Event] / [SideEffect]
     */
    suspend fun invoke(
        state: State,
        event: Event,
    ): Next

    /**
     * This object is used to update the engine after an event has been handled.
     *
     * nextState - is used to update the [State] of the engine after the event occurred.
     * nextEvent - is the next Event that will be invoked after the current event has been handled.
     * sideEffect - is a [SideEffect] that will be emitted outside of the engine.
     */
    data class Next(
        val nextState: State,
        val nextEvent: Event? = null,
        val sideEffect: SideEffect? = null,
    )
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
@Suppress("unused")
private interface EventHandlerPluginPoint
