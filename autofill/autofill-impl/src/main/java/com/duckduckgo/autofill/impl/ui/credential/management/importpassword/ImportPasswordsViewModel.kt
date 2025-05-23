/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsViaDesktopSyncDataStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.UserJourneyEndRecorder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedOut
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesViewModel(AppScope::class)
class ImportPasswordsViewModel @Inject constructor(
    private val pixel: Pixel,
    private val deviceSyncState: DeviceSyncState,
    private val dispatchers: DispatcherProvider,
    private val importPasswordsDataStore: ImportPasswordsViaDesktopSyncDataStore,
    private val userJourneyEndRecorder: UserJourneyEndRecorder,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ViewModel() {

    private var userTookAction = false

    fun userLeavingScreen() {
        if (!userTookAction) {
            pixel.fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
        }
    }

    /**
     * When user launches this screen we want to record the time they started the user-journey.
     *
     * Store timestamp and start the user-journey success clock if:
     *     sync has NOT been enabled
     *     or sync is enabled but not synced with any desktop devices
     */
    fun userLaunchedScreen() {
        logcat(VERBOSE) { "User launched import passwords screen; checking user-journey status" }

        // use appCoroutineScope as the call to get account state might not be quick; we want the call to succeed even if the user leaves the screen
        appCoroutineScope.launch(dispatchers.io()) {
            if (aUserJourneyIsOngoing()) {
                userLaunchedScreenWhileUserJourneyOngoing()
            } else {
                startANewJourney()
            }
        }
    }

    private suspend fun userLaunchedScreenWhileUserJourneyOngoing() {
        if (userJourneySuccessCriteriaMet()) {
            userJourneyEndRecorder.recordSuccessfulJourney()
        } else {
            recordUserJourneyRestartTime()
        }
    }

    private suspend fun startANewJourney() {
        when (val accountState = deviceSyncState.getAccountState()) {
            SignedOut -> recordUserJourneyStartTime()
            is SignedIn -> {
                if (!accountState.isSyncedWithDesktopDevice()) {
                    recordUserJourneyStartTime()
                }
            }
        }
    }

    private fun userJourneySuccessCriteriaMet(): Boolean {
        val state = deviceSyncState.getAccountState()
        if (state.isSyncedWithDesktopDevice()) {
            logcat(INFO) { "user-journey completed immediately upon returning to screen" }
            return true
        }
        return false
    }

    fun onUserClickedGetDesktopAppButton() {
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_GET_DESKTOP_BROWSER)
        userTookAction = true
    }

    fun onUserClickedSyncWithDesktopButton() {
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_SYNC_WITH_DESKTOP)
        userTookAction = true
    }

    private fun SyncAccountState.isSyncedWithDesktopDevice(): Boolean {
        if (this !is SignedIn) return false

        return this.devices.any { it.deviceType == DeviceSyncState.Type.DESKTOP }
    }

    private suspend fun recordUserJourneyStartTime() {
        logcat(INFO) { "Starting user-journey success clock for import passwords screen" }
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED)
        importPasswordsDataStore.startUserJourney()
    }

    private suspend fun recordUserJourneyRestartTime() {
        logcat(INFO) { "Restarting user-journey success clock for import passwords screen" }
        pixel.fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED)
        importPasswordsDataStore.startUserJourney()
    }

    private suspend fun aUserJourneyIsOngoing() = importPasswordsDataStore.getUserJourneyStartTime() != null

    fun userReturnedFromSyncSettings() {
        logcat(VERBOSE) { "User returned from sync settings. Checking user-journey status" }
        // use appCoroutineScope as the call to get account state might not be quick; we want the call to succeed even if the user leaves the screen
        appCoroutineScope.launch(dispatchers.io()) {
            if (aUserJourneyIsOngoing() && userJourneySuccessCriteriaMet()) {
                userJourneyEndRecorder.recordSuccessfulJourney()
            }
        }
    }
}
