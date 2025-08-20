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
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder.Name
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

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
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.GET_CURRENT_USER_PROFILE

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
                jsMessaging.sendResponse(
                    jsMessage = jsMessage,
                    response = PirWebMessageResponse.DefaultResponse.SUCCESS,
                )
                return@launch
            }

            val names = profiles.map { Name(it.firstName, it.middleName, it.lastName) }
            val addresses = profiles.map { it.addresses }.flatten()
            val birthYear = profiles.firstOrNull()?.birthYear ?: 0

            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.GetCurrentUserProfileResponse(
                    names = names.map {
                        PirWebMessageResponse.GetCurrentUserProfileResponse.Name(
                            first = it.firstName,
                            middle = it.middleName ?: "",
                            last = it.lastName,
                        )
                    },
                    addresses = addresses.map {
                        PirWebMessageResponse.GetCurrentUserProfileResponse.Address(
                            city = it.city,
                            state = it.state,
                        )
                    },
                    birthYear = birthYear,
                ),
            )
        }
    }
}
