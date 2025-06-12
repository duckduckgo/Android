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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.Event.Idle
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.SideEffect.None
import com.duckduckgo.pir.internal.common.actions.PirActionsRunnerStateEngine.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.logcat

class RealPirActionsRunnerStateEngine(
    private val eventHandlers: PluginPoint<EventHandler>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    runType: RunType,
    brokerSteps: List<BrokerStep>,
) : PirActionsRunnerStateEngine {
    private var engineState: State = State(runType, brokerSteps)
    private val sideEffectFlow = MutableStateFlow<SideEffect>(None)
    private val eventsFlow = MutableStateFlow<Event>(Idle)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            eventsFlow.collect {
                handleEvent(it)
            }
        }
    }

    override val sideEffect: Flow<SideEffect> = sideEffectFlow.asStateFlow()

    override fun dispatch(event: Event) {
        coroutineScope.launch {
            eventsFlow.emit(event)
        }
    }

    private suspend fun handleEvent(newEvent: Event) {
        val eventHandler = eventHandlers.getPlugins().firstOrNull { it.event.isInstance(newEvent) }
        if (eventHandler == null) {
            logcat { "PIR-ENGINE: Unable to handle event $newEvent" }
            return
        }

        logcat { "PIR-ENGINE: $newEvent dispatched to $eventHandler" }

        val next = eventHandler.invoke(engineState, newEvent)

        logcat { "PIR-ENGINE: Event resulted to state: ${next.nextState}" }
        logcat { "PIR-ENGINE: Event resulted to event: ${next.nextEvent}" }
        logcat { "PIR-ENGINE: Event resulted to sideeffect: ${next.sideEffect}" }
        engineState = next.nextState

        next.sideEffect?.let {
            logcat { "PIR-ENGINE: Emitting side effect: $it" }
            sideEffectFlow.emit(it)
        }

        next.nextEvent?.let {
            logcat { "PIR-ENGINE: Dispatching event: $it" }
            eventsFlow.emit(next.nextEvent)
        }
    }
}
