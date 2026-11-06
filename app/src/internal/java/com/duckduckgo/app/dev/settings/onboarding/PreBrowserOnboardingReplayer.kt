/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.onboarding

import android.app.Activity
import android.content.Intent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.ComposeOnboardingDialogsStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PreBrowserOnboardingReplayer @Inject constructor(
    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator,
    private val userStageStore: UserStageStore,
    private val settingsDataStore: SettingsDataStore,
    private val composeOnboardingDialogsStore: ComposeOnboardingDialogsStore,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun prepareReplay(useComposeDialogs: Boolean) {
        // Abort awaits the existing plan's terminal writes before resetting the launch stage.
        linearOnboardingOrchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        withContext(dispatchers.io()) {
            userStageStore.moveToStage(AppStage.NEW)
            settingsDataStore.hideTips = false
            composeOnboardingDialogsStore.useComposeDialogs = useComposeDialogs
        }
    }
}

fun Activity.relaunchIntoOnboarding() {
    val launchIntent = requireNotNull(packageManager.getLaunchIntentForPackage(packageName))
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(launchIntent)
}
