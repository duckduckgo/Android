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
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
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

    override val messageNames: List<PirDashboardWebMessages> =
        listOf(PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE)

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: process $jsMessage" }

        val firstName = jsMessage.params.getStringParam(PirDashboardWebConstants.PARAM_FIRST_NAME)
        val middleName = jsMessage.params.getStringParam(PirDashboardWebConstants.PARAM_MIDDLE_NAME)
        val lastName = jsMessage.params.getStringParam(PirDashboardWebConstants.PARAM_LAST_NAME)

        // attempting to add an empty name should return success=false
        if (firstName == null || lastName == null) {
            logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: missing first and/or last names" }
            jsMessaging.sendPirResponse(
                jsMessage = jsMessage,
                success = false,
            )
            return
        }

        // attempting to add a duplicate name should return success=false
        if (pirWebOnboardingStateHolder.names.any { it.firstName == firstName && it.middleName == middleName && it.lastName == lastName }) {
            logcat { "PIR-WEB: PirWebAddNameToCurrentUserProfileMessageHandler: duplicate name detected" }
            jsMessaging.sendPirResponse(
                jsMessage = jsMessage,
                success = false,
            )
            return
        }

        // Add the name to the current user profile
        pirWebOnboardingStateHolder.names.add(
            PirWebOnboardingStateHolder.Name(
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
            ),
        )

        jsMessaging.sendPirResponse(
            jsMessage = jsMessage,
            success = true,
        )
    }
}
