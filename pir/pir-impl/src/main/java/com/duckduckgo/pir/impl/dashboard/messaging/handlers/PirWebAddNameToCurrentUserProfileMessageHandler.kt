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

/**
 * Handles the message from Web to add a name the user inputted to the current user profile.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebAddNameToCurrentUserProfileMessageHandler @Inject constructor(
    private val pirWebOnboardingStateHolder: PirWebOnboardingStateHolder,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: process $jsMessage" }

        val request = jsMessage.toRequestMessage(PirWebMessageRequest.AddNameToCurrentUserProfileRequest::class)

        // attempting to add an empty name should return success=false
        if (request == null || request.first.isEmpty() || request.last.isEmpty()) {
            logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: missing first and/or last names" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        // attempting to add a duplicate name should return success=false
        if (pirWebOnboardingStateHolder.names.any { it.firstName == request.first && it.middleName == request.middle && it.lastName == request.last }) {
            logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: duplicate name detected" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        // Add the name to the current user profile
        pirWebOnboardingStateHolder.names.add(
            PirWebOnboardingStateHolder.Name(
                firstName = request.first,
                middleName = request.middle,
                lastName = request.last,
            ),
        )

        jsMessaging.sendResponse(
            jsMessage = jsMessage,
            response = PirWebMessageResponse.DefaultResponse.SUCCESS,
        )
    }
}
