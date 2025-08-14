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
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder.Name
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles the initial getCurrentUserProfile message from Web which is used to retrieve the current user profile
 * and decide whether to show the onboarding or the dashboard.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebGetCurrentUserProfileMessageHandler @Inject constructor(
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) :
    PirWebJsMessageHandler() {

    override val messageNames: List<PirDashboardWebMessages> = listOf(PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebGetCurrentUserProfileMessageHandler: process $jsMessage" }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val profiles = repository.getUserProfileQueries()

            if (profiles.isEmpty()) {
                logcat { "PIR-WEB: GetCurrentUserProfileMessageHandler: no user profiles found" }
                jsMessaging.sendPirResponse(
                    jsMessage = jsMessage,
                    success = true,
                )
                return@launch
            }

            val names = profiles.map { Name(it.firstName, it.middleName, it.lastName) }
            val addresses = profiles.map { it.addresses }.flatten()
            val birthYear = profiles.firstOrNull()?.birthYear ?: 0

            jsMessaging.sendPirResponse(
                jsMessage = jsMessage,
                success = true,
                customParams = mapOf(
                    PARAM_ADDRESSES to JSONArray().apply {
                        addresses.forEach { address ->
                            put(
                                JSONObject().apply {
                                    put(PirDashboardWebConstants.PARAM_CITY, address.city)
                                    put(PirDashboardWebConstants.PARAM_STATE, address.state)
                                },
                            )
                        }
                    },
                    PARAM_BIRTH_YEAR to birthYear,
                    PARAM_NAMES to JSONArray().apply {
                        names.forEach { name ->
                            put(
                                JSONObject().apply {
                                    put(PirDashboardWebConstants.PARAM_FIRST_NAME, name.firstName)
                                    put(PirDashboardWebConstants.PARAM_MIDDLE_NAME, name.middleName ?: "")
                                    put(PirDashboardWebConstants.PARAM_LAST_NAME, name.lastName)
                                },
                            )
                        }
                    },
                ),
            )
        }
    }

    companion object {
        private const val PARAM_ADDRESSES = "addresses"
        private const val PARAM_BIRTH_YEAR = "birthYear"
        private const val PARAM_NAMES = "names"
    }
}
