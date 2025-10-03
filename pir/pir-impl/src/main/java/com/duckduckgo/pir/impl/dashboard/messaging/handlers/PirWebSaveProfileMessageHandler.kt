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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Handles the message from Web to store the user profile information to local storage.
 */
@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = PirWebJsMessageHandler::class,
)
class PirWebSaveProfileMessageHandler @Inject constructor(
    private val pirWebProfileStateHolder: PirWebProfileStateHolder,
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    private val scanScheduler: PirScanScheduler,
    private val currentTimeProvider: CurrentTimeProvider,
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
        if (!pirWebProfileStateHolder.isProfileComplete) {
            logcat { "PIR-WEB: PirWebSaveProfileMessageHandler: incomplete profile information" }
            jsMessaging.sendResponse(
                jsMessage = jsMessage,
                response = PirWebMessageResponse.DefaultResponse.ERROR,
            )
            return
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val isProfileUpdateSuccess = handleProfileQueryUpdates()

            if (!isProfileUpdateSuccess) {
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

            pirWebProfileStateHolder.clear()
        }
    }

    /**
     * Storing the profile queries is a bit more complex than just replacing them as we need to consider
     * that some profile queries might have already extracted profiles associated to them. In that case,
     * we cannot delete those profile queries but we need to mark them as deprecated instead.
     *
     * This is so that the opt-outs for those deprecated profiles can be completed.
     *
     * https://app.asana.com/1/137249556945/project/481882893211075/task/1211369193255074?focus=true
     */
    private suspend fun handleProfileQueryUpdates(): Boolean {
        // profiles queries that already exist in the database
        // TODO consider moving the deprecated filtering to the DB layer when updating job handling for new profiles
        val existingProfileQueries = repository.getUserProfileQueries().filterNot { it.deprecated }

        // new profile queries that are the result of user changes (editing names or addresses)
        val newProfileQueries = pirWebProfileStateHolder.toProfileQueries(currentTimeProvider.localDateTimeNow().year)

        // the profile queries we need to create are the one that exist in the new ones but not in the database
        val profileQueriesToCreate = newProfileQueries.filter { newProfileQuery ->
            existingProfileQueries.none { existingProfileQuery ->
                // ignore ID when comparing as database profile queries will have an actual id
                newProfileQuery == existingProfileQuery.copy(id = 0)
            }
        }

        // the ones that we need to remove are the ones that exist in the database but not in the new ones
        val profileQueriesToRemove = existingProfileQueries.filter { existingProfileQuery ->
            newProfileQueries.none { newProfileQuery ->
                // ignore ID when comparing as database profile queries will have an actual id
                newProfileQuery == existingProfileQuery.copy(id = 0)
            }
        }.toMutableList()

        // if profile query has extracted profiles associated to it, do not delete it but mark it as deprecated
        val profileQueriesToUpdate = mutableListOf<ProfileQuery>()
        val extractedProfileQueryIds = repository.getAllExtractedProfiles().map { it.profileQueryId }.toSet()
        profileQueriesToRemove.removeAll { profileQueryToRemove ->
            if (profileQueryToRemove.id in extractedProfileQueryIds) {
                profileQueriesToUpdate.add(profileQueryToRemove.copy(deprecated = true))
                true
            } else {
                false
            }
        }

        if (profileQueriesToCreate.isEmpty() && profileQueriesToUpdate.isEmpty() && profileQueriesToRemove.isEmpty()) {
            // nothing to do
            return true
        }

        // store the changes in a single transaction to ensure data consistency
        return repository.updateProfileQueries(
            profileQueriesToAdd = profileQueriesToCreate,
            profileQueriesToUpdate = profileQueriesToUpdate,
            profileQueryIdsToDelete = profileQueriesToRemove.map { it.id },
        )
    }

    private fun startAndScheduleInitialScan() {
        context.startForegroundService(Intent(context, PirForegroundScanService::class.java))
        scanScheduler.scheduleScans()
    }
}
