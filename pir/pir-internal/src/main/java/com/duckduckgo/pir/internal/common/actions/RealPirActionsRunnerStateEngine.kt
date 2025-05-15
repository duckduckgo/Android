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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.Idle
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.None
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import logcat.logcat

class RealPirActionsRunnerStateEngine(
    private val eventHandlers: PluginPoint<EventHandler>,
    private val dispatcherProvider: DispatcherProvider,
    runType: RunType,
    brokers: List<BrokerStep>,
) : PirActionsRunnerStateEngine {
    private var engineState: State = State(runType, brokers)

    private var lastDispatchedEvent: Event = Idle

    private val sideEffectFlow = MutableStateFlow<SideEffect>(None)

    override val sideEffect: StateFlow<SideEffect> = sideEffectFlow.asStateFlow()

    override suspend fun dispatch(event: Event): Unit = withContext(dispatcherProvider.io()) {
        if (event != lastDispatchedEvent) {
            logcat { "PIR-ENGINE: New event dispatched $event" }
            lastDispatchedEvent = event

            val eventHandler = eventHandlers.getPlugins().firstOrNull { it.event.isInstance(event) }
            if (eventHandler == null) {
                logcat { "PIR-ENGINE: Unable to handle event $event" }
                return@withContext
            }

            logcat { "PIR-ENGINE: $event dispatched to $eventHandler" }

            val next = eventHandler.invoke(engineState, event)

            logcat { "PIR-ENGINE: Event resulted to state: ${next.nextState}" }
            logcat { "PIR-ENGINE: Event resulted to event: ${next.nextEvent}" }
            logcat { "PIR-ENGINE: Event resulted to sideeffect: ${next.sideEffect}" }
            engineState = next.nextState

            next.sideEffect?.let {
                logcat { "PIR-ENGINE: Emitting side effect: $it" }
                sideEffectFlow.emit(it)
            } ?: sideEffectFlow.emit(None)

            next.nextEvent?.let {
                logcat { "PIR-ENGINE: Dispatching event: $it" }
                dispatch(next.nextEvent)
            }
        }
    }
}
