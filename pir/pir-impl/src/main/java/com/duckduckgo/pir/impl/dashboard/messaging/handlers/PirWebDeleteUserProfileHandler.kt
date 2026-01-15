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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages.DELETE_USER_PROFILE_DATA
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebDeleteUserProfileHandler @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val workHandler: PirWorkHandler,
    private val pirFeatureDataCleaner: PirFeatureDataCleaner,
    private val pirWebProfileStateHolder: PirWebProfileStateHolder,
) : PirWebJsMessageHandler() {
    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebDeleteUserProfileHandler: process $jsMessage" }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                pirWebProfileStateHolder.clear()
                workHandler.cancelWork()
                pirFeatureDataCleaner.removeUserData()
            }.onFailure {
                jsMessaging.sendResponse(
                    jsMessage = jsMessage,
                    response = PirWebMessageResponse.DefaultResponse.ERROR,
                )
            }.onSuccess {
                jsMessaging.sendResponse(
                    jsMessage = jsMessage,
                    response = PirWebMessageResponse.DefaultResponse.SUCCESS,
                )
            }
        }
    }

    override val message: PirDashboardWebMessages
        get() = DELETE_USER_PROFILE_DATA
}
