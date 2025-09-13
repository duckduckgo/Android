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
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionBottomSheetDialogFactory
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionRepository
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat

interface NewAddressBarOptionManager {
    suspend fun showDialog(
        activity: Activity,
        launchedFromExternal: Boolean,
        isFreshLaunch: Boolean,
        isLightModeEnabled: Boolean,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewAddressBarOptionManager @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val userStageStore: UserStageStore,
    private val duckChat: DuckChat,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val newAddressBarOptionRepository: NewAddressBarOptionRepository,
    private val settingsDataStore: SettingsDataStore,
    private val newAddressBarOptionBottomSheetDialogFactory: NewAddressBarOptionBottomSheetDialogFactory,
) : NewAddressBarOptionManager {

    private val showDialogMutex = Mutex()

    private suspend fun shouldTrigger(
        activity: Activity,
        isFreshLaunch: Boolean,
        launchedFromExternal: Boolean,
    ): Boolean {
        logcat(DEBUG) {
            "NewAddressBarOptionManager: shouldTrigger: " +
                "launchedFromExternal=$launchedFromExternal"
        }
        return isActivityValid(activity) &&
            isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            isFeatureFlagEnabled() &&
            isSubsequentLaunch(isFreshLaunch) &&
            !isDuckAiOmnibarShortcutDisabled() &&
            !isInputScreenEnabled() &&
            !hasNewAddressBarOptionBeenShown() &&
            !launchedFromExternal &&
            !hasInteractedWithSearchAndDuckAiAnnouncement() &&
            !hasBottomAddressBarEnabled()
    }

    override suspend fun showDialog(
        activity: Activity,
        launchedFromExternal: Boolean,
        isFreshLaunch: Boolean,
        isLightModeEnabled: Boolean,
    ) {
        showDialogMutex.withLock {
            if (shouldTrigger(activity, isFreshLaunch, launchedFromExternal)) {
                newAddressBarOptionRepository.setAsShown()
                withContext(Dispatchers.Main) {
                    newAddressBarOptionBottomSheetDialogFactory.create(
                        context = activity,
                        isLightModeEnabled = isLightModeEnabled,
                    ).show()
                }
            }
        }
    }

    private fun isActivityValid(activity: Activity): Boolean {
        return (!activity.isFinishing && !activity.isDestroyed).also { result ->
            logcat(DEBUG) {
                "NewAddressBarOptionManager: isActivityValid: " +
                    "isFinishing=${activity.isFinishing}, isDestroyed=${activity.isDestroyed}, result=$result"
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

    private suspend fun hasNewAddressBarOptionBeenShown(): Boolean =
        newAddressBarOptionRepository.wasShown().also {
            logcat(DEBUG) { "NewAddressBarOptionManager: hasNewAddressBarOption: $it" }
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

    private suspend fun isSubsequentLaunch(isFreshLaunch: Boolean): Boolean {
        val wasValidated = newAddressBarOptionRepository.wasValidated()
        logcat(DEBUG) { "NewAddressBarOptionManager: isSubsequentLaunch: wasValidated=$wasValidated" }

        return if (wasValidated) {
            return (isFreshLaunch || newAddressBarOptionRepository.wasBackgrounded()).also { result ->
                logcat(DEBUG) {
                    "NewAddressBarOptionManager: isSubsequentLaunch: " +
                        "isFreshLaunch=$isFreshLaunch, wasBackgrounded=${newAddressBarOptionRepository.wasBackgrounded()}, result=$result"
                }
            }
        } else {
            logcat(DEBUG) { "NewAddressBarOptionManager: isSubsequentLaunch: Not been validated before, setting as validated and returning false" }
            newAddressBarOptionRepository.setAsValidated()
            false
        }
    }
}
