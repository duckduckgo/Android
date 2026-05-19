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

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class LaunchViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val appReferrerStateListener: AppInstallationReferrerStateListener,
    private val pixel: Pixel,
    private val testScenarioSeeder: TestScenarioSeeder,
) : ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data object Onboarding : Command()
        data class Home(val replaceExistingSearch: Boolean = false) : Command()
    }

    fun start(intent: Intent) {
        viewModelScope.launch {
            seedTestScenario(intent)
            waitForReferrerData()
            showOnboardingOrHome()
        }
    }

    private suspend fun seedTestScenario(intent: Intent) {
        runCatching {
            testScenarioSeeder.seedIfNeeded(
                isMaestroExtra = intent.getStringExtra(TestScenarioSeeder.EXTRA_IS_MAESTRO),
                scenarioKey = intent.getStringExtra(TestScenarioSeeder.EXTRA_TEST_SCENARIO),
                omnibarPosition = intent.getStringExtra(TestScenarioSeeder.EXTRA_OMNIBAR_POSITION),
                nativeInputToggle = intent.getStringExtra(TestScenarioSeeder.EXTRA_NATIVE_INPUT_TOGGLE),
                inputScreenWithAI = intent.getStringExtra(TestScenarioSeeder.EXTRA_INPUT_WITH_AI_TOGGLE),
            )
        }
        // runCatching swallows CancellationException; re-check so a cancelled viewModelScope
        // (activity finished mid-launch) stops the rest of start() instead of routing into a dead UI.
        currentCoroutineContext().ensureActive()
    }

    suspend fun showOnboardingOrHome() {
        if (userStageStore.isNewUser()) {
            command.value = Command.Onboarding
        } else {
            command.value = Command.Home()
        }
    }

    private suspend fun waitForReferrerData() {
        val startTime = System.currentTimeMillis()

        withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) {
            logcat { "Waiting for referrer" }
            return@withTimeoutOrNull appReferrerStateListener.waitForReferrerCode()
        } ?: onReferrerTimeout()

        logcat { "Waited ${System.currentTimeMillis() - startTime}ms for referrer" }
    }

    private fun onReferrerTimeout() {
        logcat(LogPriority.ERROR) { "LaunchViewModel timed out waiting for referrer" }
        pixel.fire(AppPixelName.TIMEOUT_WAITING_FOR_APP_REFERRER)
    }
}
