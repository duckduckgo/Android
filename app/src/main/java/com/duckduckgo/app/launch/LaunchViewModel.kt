/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.pixels.AppPixelName.SPLASHSCREEN_FAILED_TO_LAUNCH
import com.duckduckgo.app.pixels.AppPixelName.SPLASHSCREEN_SHOWN
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class LaunchViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val appReferrerStateListener: AppInstallationReferrerStateListener,
    private val pixel: Pixel,
) :
    ViewModel() {

    private var splashScreenFailToExitJob: Job? = null

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data object Onboarding : Command()
        data class Home(val replaceExistingSearch: Boolean = false) : Command()
    }

    fun sendWelcomeScreenPixel() {
        pixel.fire(SPLASHSCREEN_SHOWN)
    }

    suspend fun determineViewToShow() {
        waitForReferrerData()

        if (userStageStore.isNewUser()) {
            command.value = Command.Onboarding
        } else {
            command.value = Command.Home()
        }
    }

    fun launchSplashScreenFailToExitJob(launcherPackageName: String?) {
        splashScreenFailToExitJob = viewModelScope.launch {
            delay(1.5.seconds)
            sendWelcomeScreenPixel()
            determineViewToShow()
            sendSplashScreenFailedToLaunchPixel(launcherPackageName)
        }
    }

    fun cancelSplashScreenFailToExitJob() {
        splashScreenFailToExitJob?.cancel()
    }

    private suspend fun waitForReferrerData() {
        val startTime = System.currentTimeMillis()

        withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) {
            Timber.d("Waiting for referrer")
            return@withTimeoutOrNull appReferrerStateListener.waitForReferrerCode()
        }

        Timber.d("Waited ${System.currentTimeMillis() - startTime}ms for referrer")
    }

    // Temporary to track splashscreen errors
    private fun sendSplashScreenFailedToLaunchPixel(launcherPackageName: String?) {
        val resolvedLauncherPackageName = launcherPackageName ?: "unknown"
        val params = mapOf(
            "launcherPackageName" to resolvedLauncherPackageName,
            "api" to android.os.Build.VERSION.SDK_INT.toString(),
        )
        pixel.fire(pixel = SPLASHSCREEN_FAILED_TO_LAUNCH, parameters = params)
    }
}
