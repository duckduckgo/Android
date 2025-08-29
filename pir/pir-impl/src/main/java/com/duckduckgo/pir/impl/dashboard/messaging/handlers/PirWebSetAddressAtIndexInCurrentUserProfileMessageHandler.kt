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
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler @Inject constructor(
    private val pirWebOnboardingStateHolder: PirWebOnboardingStateHolder,
) : PirWebJsMessageHandler() {

    override val message: PirDashboardWebMessages = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler: process $message" }

        val request = jsMessage.toRequestMessage(PirWebMessageRequest.SetAddressAtIndexForCurrentUserProfileRequest::class)

        val index = request?.index ?: 0
        val city = request?.address?.city?.trim().orEmpty()
        val state = request?.address?.state?.trim().orEmpty()

        if (city.isBlank() || state.isBlank()) {
            logcat { "PIR-WEB: PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler: missing city and/or state" }

            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        // attempting to add a duplicate address should return success=false
        if (!pirWebOnboardingStateHolder.setAddressAtIndex(
                index = index,
                city = city,
                state = state,
            )
        ) {
            logcat { "PIR-WEB: PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler: failed to set address at index $index" }

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
