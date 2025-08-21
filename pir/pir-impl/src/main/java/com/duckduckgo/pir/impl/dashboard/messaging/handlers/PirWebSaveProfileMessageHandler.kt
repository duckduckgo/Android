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

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Handles the message from Web to store the user profile information to local storage.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebSaveProfileMessageHandler @Inject constructor(
    private val pirWebOnboardingStateHolder: PirWebOnboardingStateHolder,
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    private val scanScheduler: PirScanScheduler,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirWebJsMessageHandler() {

    override val message = PirDashboardWebMessages.SAVE_PROFILE

    override fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    ) {
        logcat { "PIR-WEB: PirWebSaveProfileMessageHandler: process $jsMessage" }

        // validate that we have the complete profile information
        if (!pirWebOnboardingStateHolder.isProfileComplete) {
            logcat { "PIR-WEB: PirWebSaveProfileMessageHandler: incomplete profile information" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val profiles = pirWebOnboardingStateHolder.toUserProfiles()
            if (!repository.saveUserProfiles(profiles)) {
                logcat { "PIR-WEB: PirWebSaveProfileMessageHandler: failed to save all user profiles" }
                jsMessaging.sendResponse(
                    jsMessage = jsMessage,
                    response = PirWebMessageResponse.DefaultResponse.ERROR,
                )
                return@launch
            }

            jsMessaging.sendResponse(
                jsMessage,
                response = PirWebMessageResponse.DefaultResponse.SUCCESS,
            )

            // start the initial scan at this point as startScanAndOptOut message is not reliable
            startAndScheduleInitialScan()

            pirWebOnboardingStateHolder.clear()
        }
    }

    private fun startAndScheduleInitialScan() {
        context.startForegroundService(Intent(context, PirForegroundScanService::class.java))
        scanScheduler.scheduleScans()
    }
}
