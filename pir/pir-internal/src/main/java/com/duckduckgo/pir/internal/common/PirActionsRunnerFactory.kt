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

package com.duckduckgo.pir.internal.common

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.pir.internal.scripts.PirMessagingInterface
import com.duckduckgo.pir.internal.scripts.RealBrokerActionProcessor
import com.duckduckgo.pir.internal.store.PirRepository
import javax.inject.Inject

class PirActionsRunnerFactory @Inject constructor(
    private val pirDetachedWebViewProvider: PirDetachedWebViewProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val jsMessageHelper: JsMessageHelper,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirRunStateHandler: PirRunStateHandler,
    private val pirRepository: PirRepository,
    private val captchaResolver: CaptchaResolver,
) {
    /**
     * Every instance of PirActionsRunner is created with its own instance of [PirMessagingInterface] and [RealBrokerActionProcessor]
     */
    fun createInstance(
        context: Context,
        pirScriptToLoad: String,
        runType: RunType,
    ): PirActionsRunner {
        return RealPirActionsRunner(
            dispatcherProvider,
            pirDetachedWebViewProvider,
            RealBrokerActionProcessor(
                PirMessagingInterface(
                    jsMessageHelper,
                ),
            ),
            context,
            pirScriptToLoad,
            runType,
            currentTimeProvider,
            RealNativeBrokerActionHandler(
                pirRepository,
                dispatcherProvider,
                captchaResolver,
            ),
            pirRunStateHandler,
        )
    }

    enum class RunType {
        MANUAL,
        SCHEDULED,
        OPTOUT,
    }
}
