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

import android.content.Context
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionBottomSheetDialogFactory
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionRepository
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat

interface NewAddressBarOptionManager {
    suspend fun showDialog(context: Context, launchedFromExternal: Boolean, interstitialScreen: Boolean, isLightModeEnabled: Boolean)
}

class RealNewAddressBarOptionManager(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val userStageStore: UserStageStore,
    private val duckChat: DuckChat,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val newAddressBarOptionRepository: NewAddressBarOptionRepository,
    private val settingsDataStore: SettingsDataStore,
    private val newAddressBarOptionBottomSheetDialogFactory: NewAddressBarOptionBottomSheetDialogFactory,
) : NewAddressBarOptionManager {

    private suspend fun shouldTrigger(launchedFromExternal: Boolean, interstitialScreen: Boolean): Boolean {
        logcat(DEBUG) {
            "NewAddressBarOptionManager: shouldTrigger: " +
                "launchedFromExternal=$launchedFromExternal, interstitialScreen=$interstitialScreen"
        }
        return isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            isFeatureFlagEnabled() &&
            isSubsequentLaunch() &&
            !isDuckAiOmnibarShortcutDisabled() &&
            !isInputScreenEnabled() &&
            !hasForceChoiceBeenShown() &&
            !launchedFromExternal &&
            !interstitialScreen &&
            !hasInteractedWithSearchAndDuckAiAnnouncement() &&
            !hasBottomAddressBarEnabled()
    }

    override suspend fun showDialog(
        context: Context,
        launchedFromExternal: Boolean,
        interstitialScreen: Boolean,
        isLightModeEnabled: Boolean,
    ) {
        if (shouldTrigger(launchedFromExternal, interstitialScreen)) {
            withContext(Dispatchers.Main) {
                newAddressBarOptionBottomSheetDialogFactory.create(
                    context = context,
                    isLightModeEnabled = isLightModeEnabled,
                ).show()
            }
        }
    }

    private fun isDuckAiEnabled(): Boolean =
        duckChat.isEnabled().also {
            logcat(DEBUG) { "NewAddressBarOptionManager: isDuckAiEnabled: $it" }
        }

    private suspend fun isOnboardingCompleted(): Boolean =
        (userStageStore.getUserAppStage() == AppStage.ESTABLISHED).also { result ->
            logcat(DEBUG) { "NewAddressBarOptionManager: isOnboardingCompleted: appStage=${userStageStore.getUserAppStage()}, result=$result" }
        }

    private fun isFeatureFlagEnabled(): Boolean =
        duckAiFeatureState.showNewAddressBarOptionAnnouncement.value.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: isFeatureFlagEnabled: $it" }
        }

    private fun isDuckAiOmnibarShortcutDisabled(): Boolean =
        (!duckAiFeatureState.showOmnibarShortcutInAllStates.value).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: isDuckAiOmnibarShortcutDisabled: $it" }
        }

    private fun isInputScreenEnabled(): Boolean =
        duckAiFeatureState.showInputScreen.value.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: isInputScreenEnabled: $it" }
        }

    private suspend fun hasForceChoiceBeenShown(): Boolean =
        newAddressBarOptionRepository.hasBeenShown().also {
            logcat(DEBUG) { "NewAddressBarOptionManager: hasForceChoiceBeenShown: $it" }
        }

    private fun hasInteractedWithSearchAndDuckAiAnnouncement(): Boolean =
        remoteMessagingRepository.dismissedMessages().let { dismissedMessages ->
            dismissedMessages.contains("search_duck_ai_announcement").also { result ->
                logcat(DEBUG) {
                    "NewAddressBarOptionManager: hasInteractedWithSearchAndDuckAiAnnouncement: " +
                        "dismissedMessages=$dismissedMessages, result=$result"
                }
            }
        }

    private fun hasBottomAddressBarEnabled(): Boolean =
        (settingsDataStore.omnibarPosition == OmnibarPosition.BOTTOM).also { result ->
            logcat(DEBUG) {
                "NewAddressBarOptionManager: hasBottomAddressBarEnabled: " +
                    "omnibarPosition=${settingsDataStore.omnibarPosition}, result=$result"
            }
        }

    private suspend fun isSubsequentLaunch(): Boolean {
        val hasBeenChecked = newAddressBarOptionRepository.hasBeenChecked()
        logcat(DEBUG) { "NewAddressBarOptionManager: isSubsequentLaunch: hasBeenChecked=$hasBeenChecked" }

        return if (hasBeenChecked) {
            logcat(DEBUG) { "NewAddressBarOptionManager: isSubsequentLaunch: Checked before, returning true" }
            true
        } else {
            logcat(DEBUG) { "NewAddressBarOptionManager: isSubsequentLaunch: Not checked before, marking as checked and returning false" }
            newAddressBarOptionRepository.markAsChecked()
            false
        }
    }
}
