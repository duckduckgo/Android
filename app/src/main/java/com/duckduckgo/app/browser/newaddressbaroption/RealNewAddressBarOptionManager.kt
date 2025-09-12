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

interface NewAddressBarOptionManager {
    suspend fun showDialog(context: Context, launchedFromExternal: Boolean, isLightModeEnabled: Boolean)
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

    private suspend fun shouldTrigger(launchedFromExternal: Boolean): Boolean {
        return isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            isFeatureFlagEnabled() &&
            !isDuckAiOmnibarShortcutDisabled() &&
            !isInputScreenEnabled() &&
            !hasForceChoiceBeenShown() &&
            !launchedFromExternal &&
            !hasInteractedWithSearchAndDuckAiAnnouncement() &&
            !hasBottomAddressBarEnabled()
    }

    override suspend fun showDialog(
        context: Context,
        launchedFromExternal: Boolean,
        isLightModeEnabled: Boolean,
    ) {
        if (shouldTrigger(launchedFromExternal)) {
            newAddressBarOptionBottomSheetDialogFactory.create(
                context = context,
                isLightModeEnabled = isLightModeEnabled,
            ).show()
        }
    }

    private fun isDuckAiEnabled(): Boolean {
        return duckChat.isEnabled()
    }

    private suspend fun isOnboardingCompleted(): Boolean {
        return userStageStore.getUserAppStage() == AppStage.ESTABLISHED
    }

    private fun isFeatureFlagEnabled(): Boolean {
        return duckAiFeatureState.showNewAddressBarOptionAnnouncement.value
    }

    private fun isDuckAiOmnibarShortcutDisabled(): Boolean {
        return !duckAiFeatureState.showOmnibarShortcutInAllStates.value
    }

    private fun isInputScreenEnabled(): Boolean {
        return duckAiFeatureState.showInputScreen.value
    }

    private suspend fun hasForceChoiceBeenShown(): Boolean {
        return newAddressBarOptionRepository.getHasBeenShown()
    }

    private fun hasInteractedWithSearchAndDuckAiAnnouncement(): Boolean {
        return remoteMessagingRepository.dismissedMessages().contains("search_duck_ai_announcement")
    }

    private fun hasBottomAddressBarEnabled(): Boolean {
        return settingsDataStore.omnibarPosition == OmnibarPosition.BOTTOM
    }
}
