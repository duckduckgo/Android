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
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Handles the initial handshake message from Web which is used to establish communication.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebHandshakeMessageHandler @Inject constructor(
    private val subscriptions: Subscriptions,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.HANDSHAKE

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebHandshakeMessageHandler: process $jsMessage" }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            // Web request contains the version of the API the web uses, but no version check comparison is needed
            // per https://app.asana.com/1/137249556945/task/1205606257846476/comment/1211089906912712?focus=true
            // (PIR is backwards and forwards compatible)

            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.HandshakeResponse(
                    success = true,
                    userData = getHandshakeUserData(),
                ),
            )
        }
    }

    private suspend fun getHandshakeUserData(): PirWebMessageResponse.HandshakeResponse.UserData {
        return PirWebMessageResponse.HandshakeResponse.UserData(
            isAuthenticatedUser = subscriptions.getAccessToken() != null,
            isUserEligibleForFreeTrial = subscriptions.isFreeTrialEligible(),
        )
    }
}
