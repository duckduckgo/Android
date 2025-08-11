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

package com.duckduckgo.pir.impl.messaging.handlers

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.messaging.PirDashboardWebMessages
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.logcat
import org.json.JSONObject

/**
 * Handles the initial getCurrentUserProfile message from Web which is used to retrieve the current user profile
 * and decide whether to show the onboarding or the dashboard.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebGetCurrentUserProfileMessageHandler @Inject constructor() :
    PirWebJsMessageHandler() {

    override val methods: List<String> =
        listOf(PirDashboardWebMessages.GET_CURRENT_USER_PROFILE.messageName)

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: GetCurrentUserProfileMessageHandler: process $jsMessage" }

        jsMessaging.onResponse(
            JsCallbackData(
                params = JSONObject().apply {
                    put(PirDashboardWebConstants.PARAM_SUCCESS, false)
                    put(
                        PirDashboardWebConstants.PARAM_VERSION,
                        PirDashboardWebConstants.SCRIPT_API_VERSION,
                    )
                    // TODO: Replace with actual user profile data
                },
                featureName = jsMessage.featureName,
                method = jsMessage.method,
                id = jsMessage.id ?: "",
            ),
        )
    }
}
