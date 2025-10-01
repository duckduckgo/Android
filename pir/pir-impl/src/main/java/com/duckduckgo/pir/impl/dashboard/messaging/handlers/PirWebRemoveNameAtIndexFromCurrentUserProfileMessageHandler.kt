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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageRequest
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandler @Inject constructor(
    private val pirWebOnboardingStateHolder: PirWebOnboardingStateHolder,
) : PirWebJsMessageHandler() {

    override val message: PirDashboardWebMessages = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandler: process $message" }

        val request = jsMessage.toRequestMessage(PirWebMessageRequest.RemoveNameAtIndexFromCurrentUserProfileRequest::class)
        if (request == null || !pirWebOnboardingStateHolder.removeNameAtIndex(request.index)) {
            logcat { "PIR-WEB: PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandler: failed to remove name at index ${request?.index}" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        jsMessaging.sendResponse(
            jsMessage = jsMessage,
            response = PirWebMessageResponse.DefaultResponse.SUCCESS,
        )
    }
}
