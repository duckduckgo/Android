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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import logcat.logcat

class RealPirActionsRunnerStateEngine(
    private val eventHandlers: PluginPoint<EventHandler>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    runType: RunType,
    brokerSteps: List<BrokerStep>,
    profileQuery: ProfileQuery,
) : PirActionsRunnerStateEngine {
    private var engineState: State = State(
        runType = runType,
        brokerStepsToExecute = brokerSteps,
        profileQuery = profileQuery,
    )
    private val sideEffectFlow = MutableSharedFlow<SideEffect>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val eventsFlow = MutableSharedFlow<Event>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            eventsFlow.collect {
                handleEvent(it)
            }
        }
    }

    override val sideEffect: Flow<SideEffect> = sideEffectFlow.asSharedFlow()

    override fun dispatch(event: Event) {
        coroutineScope.launch {
            eventsFlow.emit(event)
        }
    }

    private suspend fun handleEvent(newEvent: Event) {
        val eventHandler = eventHandlers.getPlugins().firstOrNull { it.event.isInstance(newEvent) }
        if (eventHandler == null) {
            logcat { "PIR-ENGINE($this): Unable to handle event $newEvent" }
            return
        }

        logcat { "PIR-ENGINE($this): $newEvent dispatched to $eventHandler" }

        val next = eventHandler.invoke(engineState, newEvent)

        logcat { "PIR-ENGINE($this): Event resulted to state: ${next.nextState}" }
        logcat { "PIR-ENGINE($this): Event resulted to event: ${next.nextEvent}" }
        logcat { "PIR-ENGINE($this): Event resulted to sideeffect: ${next.sideEffect}" }
        engineState = next.nextState

        next.sideEffect?.let {
            logcat { "PIR-ENGINE($this): Emitting side effect: $it" }
            sideEffectFlow.emit(it)
        }

        next.nextEvent?.let {
            logcat { "PIR-ENGINE($this): Dispatching event: $it" }
            eventsFlow.emit(next.nextEvent)
        }
    }
}
