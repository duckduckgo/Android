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
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.models.ProfileQuery
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

interface PirActionsRunnerStateEngineFactory {
    fun create(
        runType: PirJob.RunType,
        brokerSteps: List<BrokerStep>,
        profileQuery: ProfileQuery,
    ): PirActionsRunnerStateEngine
}

class RealPirActionsRunnerStateEngineFactory @Inject constructor(
    private val eventHandlers: PluginPoint<EventHandler>,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : PirActionsRunnerStateEngineFactory {
    override fun create(
        runType: PirJob.RunType,
        brokerSteps: List<BrokerStep>,
        profileQuery: ProfileQuery,
    ): PirActionsRunnerStateEngine {
        return RealPirActionsRunnerStateEngine(
            eventHandlers = eventHandlers,
            dispatcherProvider = dispatcherProvider,
            coroutineScope = coroutineScope,
            runType = runType,
            brokerSteps = brokerSteps,
            profileQuery = profileQuery,
        )
    }
}
