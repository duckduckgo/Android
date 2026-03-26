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
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageRequest
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Handles the message from Web to remove an opt-out record from the dashboard.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirRemoveOptOutFromDashboardMessageHandler @Inject constructor(
    private val jobRecordUpdater: JobRecordUpdater,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.REMOVE_OPT_OUT_FROM_DASHBOARD

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirRemoveOptOutFromDashboardMessageHandler: process $jsMessage" }

        val recordId = jsMessage.toRequestMessage(
            PirWebMessageRequest.RemoveOptOutFromDashboardRequest::class,
        )?.recordId

        if (recordId == null) {
            logcat { "PIR-WEB: PirRemoveOptOutFromDashboardMessageHandler: missing recordId" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            jobRecordUpdater.markRecordsAsRemovedByUser(extractedProfileId = recordId)

            logcat { "PIR-WEB: PirRemoveOptOutFromDashboardMessageHandler: successfully removed recordId=$recordId" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.SUCCESS,
            )
        }
    }
}
