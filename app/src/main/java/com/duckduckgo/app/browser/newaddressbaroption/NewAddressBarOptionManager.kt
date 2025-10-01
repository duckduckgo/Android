/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 ( the "License" );
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

package com.duckduckgo.app.browser.newaddressbaroption

import android.app.Activity
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat
import javax.inject.Inject

interface NewAddressBarOptionManager {
    suspend fun showChoiceScreen(
        activity: DuckDuckGoActivity,
        isLaunchedFromExternal: Boolean,
    )

    suspend fun setAsShown()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewAddressBarOptionManager @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val userStageStore: UserStageStore,
    private val duckChat: DuckChat,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val newAddressBarOptionDataStore: NewAddressBarOptionDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : NewAddressBarOptionManager {
    private val showChoiceScreenMutex = Mutex()

    override suspend fun showChoiceScreen(
        activity: DuckDuckGoActivity,
        isLaunchedFromExternal: Boolean,
    ) {
        showChoiceScreenMutex.withLock {
            if (validate(activity, isLaunchedFromExternal)) {
                logcat(DEBUG) { "NewAddressBarOptionManager: All conditions met, showing choice screen" }
                newAddressBarOptionDataStore.setAsShown()
                withContext(dispatchers.main()) {
                    duckChat.showNewAddressBarOptionChoiceScreen(activity, activity.isDarkThemeEnabled())
                }
            }
        }
    }

    override suspend fun setAsShown() {
        newAddressBarOptionDataStore.setAsShown()
    }

    private suspend fun validate(
        activity: Activity,
        isLaunchedFromExternal: Boolean,
    ): Boolean {
        logcat(DEBUG) {
            "--------------------------\n" +
                "NewAddressBarOptionManager: Starting validation..." +
                "\n--------------------------"
        }
        return isActivityValid(activity) &&
            isOnboardingCompleted() &&
            hasNotShownNewAddressBarOptionAnnouncement() &&
            isDuckAiEnabled() &&
            isDuckAiOmnibarShortcutEnabled() &&
            isInputScreenDisabled() &&
            isBottomAddressBarDisabled() &&
            hasNotInteractedWithSearchAndDuckAiRMF() &&
            isNewAddressBarOptionChoiceScreenEnabled() &&
            isNotLaunchedFromExternal(isLaunchedFromExternal) &&
            isSubsequentLaunch()
    }

    private fun isActivityValid(activity: Activity): Boolean =
        (!activity.isFinishing && !activity.isDestroyed).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isActivityValid" }
        }

    private suspend fun isOnboardingCompleted(): Boolean =
        (userStageStore.getUserAppStage() == AppStage.ESTABLISHED).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isOnboardingCompleted" }
        }

    private suspend fun hasNotShownNewAddressBarOptionAnnouncement(): Boolean =
        (!newAddressBarOptionDataStore.wasShown()).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it hasNotShownNewAddressBarOptionAnnouncement" }
        }

    private fun isDuckAiEnabled(): Boolean =
        duckChat.isEnabled().also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isDuckAiEnabled" }
        }

    private fun isDuckAiOmnibarShortcutEnabled(): Boolean =
        duckAiFeatureState.showOmnibarShortcutInAllStates.value.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isDuckAiOmnibarShortcutEnabled" }
        }

    private fun isInputScreenDisabled(): Boolean =
        (!duckAiFeatureState.showInputScreen.value).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isInputScreenDisabled" }
        }

    private fun isBottomAddressBarDisabled(): Boolean =
        (settingsDataStore.omnibarPosition != OmnibarPosition.BOTTOM).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isBottomAddressBarDisabled" }
        }

    private fun hasNotInteractedWithSearchAndDuckAiRMF(): Boolean =
        remoteMessagingRepository.dismissedMessages().let { dismissedMessages ->
            (!dismissedMessages.contains("search_duck_ai_announcement")).also {
                logcat(DEBUG) { "NewAddressBarOptionManager: $it hasNotInteractedWithSearchAndDuckAiRMF" }
            }
        }

    private fun isNewAddressBarOptionChoiceScreenEnabled(): Boolean =
        duckAiFeatureState.showNewAddressBarOptionChoiceScreen.value.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isNewAddressBarOptionChoiceScreenEnabled" }
        }

    private fun isNotLaunchedFromExternal(launchedFromExternal: Boolean): Boolean =
        (!launchedFromExternal).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isNotLaunchedFromExternal" }
        }

    private suspend fun isSubsequentLaunch(): Boolean =
        if (newAddressBarOptionDataStore.wasValidated()) {
            true
        } else {
            newAddressBarOptionDataStore.setAsValidated()
            false
        }.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isSubsequentLaunch" }
        }
}
