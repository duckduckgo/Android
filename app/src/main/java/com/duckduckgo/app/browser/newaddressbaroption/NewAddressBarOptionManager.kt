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
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarSelection.SEARCH_AND_AI
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private val remoteMessageModel: RemoteMessageModel,
    private val newAddressBarOptionDataStore: NewAddressBarOptionDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val newAddressBarOptionV2BottomSheetDialogFactory: NewAddressBarOptionV2BottomSheetDialogFactory,
    private val pixel: Pixel,
    private val appTheme: AppTheme,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
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
                if (isNewAddressBarOptionChoiceScreenV2Enabled()) {
                    newAddressBarOptionDataStore.setAsShownV2()
                    withContext(dispatchers.main()) {
                        showV2ChoiceScreen(activity)
                    }
                } else {
                    newAddressBarOptionDataStore.setAsShown()
                    withContext(dispatchers.main()) {
                        duckChat.showNewAddressBarOptionChoiceScreen(activity, activity.isDarkThemeEnabled())
                    }
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
        val v2Enabled = isNewAddressBarOptionChoiceScreenV2Enabled()
        if (v2Enabled) {
            return isDuckAiEnabled() &&
                isOnboardingCompleted() &&
                isInputScreenDisabled() &&
                hasNotShownV2Announcement() &&
                isDuckAiOmnibarShortcutEnabled() &&
                isBottomAddressBarDisabled() &&
                hasNotInteractedWithSearchAndDuckAiRMF() &&
                isNotLaunchedFromExternal(isLaunchedFromExternal) &&
                isSubsequentLaunch() &&
                isActivityValid(activity)
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
            isSubsequentLaunch() &&
            hasNoInputScreenSelection()
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

    private fun hasNoInputScreenSelection(): Boolean =
        (onboardingStore.getInputScreenSelection() == null).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it hasNoInputScreenSelection" }
        }

    private fun isBottomAddressBarDisabled(): Boolean =
        (settingsDataStore.omnibarType != OmnibarType.SINGLE_BOTTOM).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isBottomAddressBarDisabled" }
        }

    private suspend fun hasNotInteractedWithSearchAndDuckAiRMF(): Boolean =
        (!remoteMessageModel.isMessageDismissed("search_duck_ai_announcement")).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it hasNotInteractedWithSearchAndDuckAiRMF" }
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

    private fun isNewAddressBarOptionChoiceScreenV2Enabled(): Boolean =
        duckAiFeatureState.showNewAddressBarOptionChoiceScreenV2.value.also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it isNewAddressBarOptionChoiceScreenV2Enabled" }
        }

    private suspend fun hasNotShownV2Announcement(): Boolean =
        (!newAddressBarOptionDataStore.wasShownV2()).also {
            logcat(DEBUG) { "NewAddressBarOptionManager: $it hasNotShownV2Announcement" }
        }

    private fun showV2ChoiceScreen(activity: DuckDuckGoActivity) {
        newAddressBarOptionV2BottomSheetDialogFactory.create(
            context = activity,
            isLightMode = appTheme.isLightModeEnabled(),
            callback =
            object : NewAddressBarV2Callback {
                override fun onDisplayed() {
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED)
                }

                override fun onConfirmed(selection: NewAddressBarSelection) {
                    if (selection == SEARCH_AND_AI) {
                        appCoroutineScope.launch {
                            duckChat.setInputScreenUserSetting(true)
                        }
                    }
                    pixel.fire(
                        AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED,
                        parameters = mapOf(SELECTION_PARAM to selection.value),
                    )
                }
            },
        ).show()
    }

    private companion object {
        private const val SELECTION_PARAM = "selection"
    }
}
