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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.daxprompts.api.DaxPrompts
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType.NONE
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType.SHOW_CONTROL
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType.SHOW_VARIANT_BROWSER_COMPARISON
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType.SHOW_VARIANT_DUCKPLAYER
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class LaunchViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val appReferrerStateListener: AppInstallationReferrerStateListener,
    private val daxPrompts: DaxPrompts,
    private val appInstallStore: AppInstallStore,
) :
    ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data object Onboarding : Command()
        data class Home(val replaceExistingSearch: Boolean = false) : Command()
        data object DaxPromptDuckPlayer : Command()
        data class PlayVideoInDuckPlayer(val url: String) : Command()
        data object DaxPromptBrowserComparison : Command()
        data object CloseDaxPrompt : Command()
    }

    suspend fun determineViewToShow() {
        waitForReferrerData()

        when (daxPrompts.evaluate()) {
            SHOW_CONTROL, NONE -> {
                logcat { "Control / None action" }
                showOnboardingOrHome()
            }

            SHOW_VARIANT_DUCKPLAYER -> {
                logcat { "Variant Duck Player action" }
                command.value = Command.DaxPromptDuckPlayer
            }

            SHOW_VARIANT_BROWSER_COMPARISON -> {
                logcat { "Variant Browser Comparison action" }
                command.value = Command.DaxPromptBrowserComparison
            }
        }
    }

    suspend fun showOnboardingOrHome() {
        if (userStageStore.isNewUser()) {
            command.value = Command.Onboarding
        } else {
            command.value = Command.Home()
        }
    }

    fun onDaxPromptDuckPlayerActivityResult(url: String? = null) {
        if (url != null) {
            command.value = Command.PlayVideoInDuckPlayer(url)
        } else {
            command.value = Command.CloseDaxPrompt
        }
    }

    fun onDaxPromptBrowserComparisonActivityResult(showComparisonChart: Boolean? = false) {
        if (showComparisonChart != null) {
            appInstallStore.defaultBrowser = showComparisonChart
        }
        command.value = Command.CloseDaxPrompt
    }

    private suspend fun waitForReferrerData() {
        val startTime = System.currentTimeMillis()

        withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) {
            logcat { "Waiting for referrer" }
            return@withTimeoutOrNull appReferrerStateListener.waitForReferrerCode()
        }

        logcat { "Waited ${System.currentTimeMillis() - startTime}ms for referrer" }
    }
}
