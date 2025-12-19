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

package com.duckduckgo.pir.impl.integration.fakes

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.common.actions.RealPirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.scripts.RealBrokerActionProcessor
import kotlinx.coroutines.CoroutineScope

/**
 * Factory to create RealPirActionsRunner instances with test dependencies.
 */
class TestPirActionsRunnerFactory(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDetachedWebViewProvider: FakePirDetachedWebViewProvider,
    private val brokerActionProcessor: RealBrokerActionProcessor,
    private val nativeBrokerActionHandler: FakeNativeBrokerActionHandler,
    private val engineFactory: RealPirActionsRunnerStateEngineFactory,
    private val coroutineScope: CoroutineScope,
) : RealPirActionsRunner.Factory {
    override fun create(
        context: Context,
        pirScriptToLoad: String,
        runType: RunType,
    ): RealPirActionsRunner {
        return RealPirActionsRunner(
            dispatcherProvider = dispatcherProvider,
            pirDetachedWebViewProvider = pirDetachedWebViewProvider,
            brokerActionProcessor = brokerActionProcessor,
            nativeBrokerActionHandler = nativeBrokerActionHandler,
            engineFactory = engineFactory,
            coroutineScope = coroutineScope,
            runType = runType,
            context = context,
            pirScriptToLoad = pirScriptToLoad,
        )
    }
}
