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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.db.EventType.MANUAL_SCAN_COMPLETED
import com.duckduckgo.pir.impl.store.db.EventType.MANUAL_SCAN_STARTED
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Handles the initial scan status message from Web which is used to retrieve the status of the initial scan.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebInitialScanStatusMessageHandler @Inject constructor(
    private val eventsRepository: PirEventsRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.INITIAL_SCAN_STATUS

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: InitialScanStatusMessageHandler: process $jsMessage" }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val eventLogs = eventsRepository.getAllEventLogsFlow().firstOrNull().orEmpty()

            // TODO get actual scan progress results from the repository
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.InitialScanResponse(
                    resultsFound = listOf(),
                    scanProgress = PirWebMessageResponse.InitialScanResponse.ScanProgress(
                        currentScan = eventLogs.count { it.eventType == MANUAL_SCAN_COMPLETED },
                        totalScans = eventLogs.count { it.eventType == MANUAL_SCAN_STARTED },
                        scannedBrokers = listOf(),
                    ),
                ),
            )
        }
    }
}
