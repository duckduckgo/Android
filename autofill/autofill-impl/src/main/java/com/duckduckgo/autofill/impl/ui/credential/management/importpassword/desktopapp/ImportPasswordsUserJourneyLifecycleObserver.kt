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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.DeviceSyncState.Type.DESKTOP
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit.HOURS
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class ImportPasswordsUserJourneyLifecycleObserver @Inject constructor(
    private val dataStore: ImportPasswordsViaDesktopSyncDataStore,
    private val deviceSyncState: DeviceSyncState,
    private val userJourneyEndRecorder: UserJourneyEndRecorder,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        appCoroutineScope.launch(dispatchers.io()) {
            val startTime = dataStore.getUserJourneyStartTime() ?: return@launch
            val duration = (System.currentTimeMillis() - startTime).toDuration(MILLISECONDS)

            if (duration <= USER_JOURNEY_MAX_DURATION) {
                userJourneyWindowStillOpen(USER_JOURNEY_MAX_DURATION - duration)
            } else {
                userJourneyWindowHasExpired()
            }
        }
    }

    private suspend fun userJourneyWindowStillOpen(timeRemaining: Duration) {
        val deviceState = deviceSyncState.getAccountState()
        if (deviceState is SignedIn && deviceState.isSyncedWithDesktopDevice()) {
            userHasNowSyncedWithDesktopDevice()
        } else {
            logcat(VERBOSE) { "Import Passwords user-journey; user has not yet synced with desktop device. time remaining: $timeRemaining" }
        }
    }

    private suspend fun userHasNowSyncedWithDesktopDevice() {
        logcat(VERBOSE) { "Import Passwords user-journey successful; now synced with desktop device" }
        userJourneyEndRecorder.recordSuccessfulJourney()
    }

    private suspend fun userJourneyWindowHasExpired() {
        logcat(VERBOSE) { "Import Passwords user-journey expired" }
        userJourneyEndRecorder.recordUnsuccessfulJourney()
    }

    private fun SignedIn.isSyncedWithDesktopDevice(): Boolean {
        return this.devices.any { it.deviceType == DESKTOP }
    }

    companion object {
        private val USER_JOURNEY_MAX_DURATION = 48.toDuration(HOURS)
    }
}
