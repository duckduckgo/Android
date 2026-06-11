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

package com.duckduckgo.app.browser.newaddressbaroption

import android.app.Activity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
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

interface NewAddressBarPickerManager {
    suspend fun showChoiceScreen(activity: DuckDuckGoActivity)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewAddressBarPickerManager @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckChat: DuckChat,
    private val userStageStore: UserStageStore,
    private val newAddressBarPickerDataStore: NewAddressBarPickerDataStore,
    private val newAddressBarPickerBottomSheetDialogFactory: NewAddressBarPickerBottomSheetDialogFactory,
    private val pixel: Pixel,
    private val appTheme: AppTheme,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : NewAddressBarPickerManager {
    private val showChoiceScreenMutex = Mutex()

    override suspend fun showChoiceScreen(activity: DuckDuckGoActivity) {
        showChoiceScreenMutex.withLock {
            if (validate(activity)) {
                logcat(DEBUG) { "NewAddressBarPickerManager: All conditions met, showing choice screen" }
                withContext(dispatchers.main()) {
                    showChoiceScreenDialog(activity)
                }
            }
        }
    }

    private suspend fun validate(activity: Activity): Boolean =
        isPickerEnabled() &&
            isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            isInputScreenNeverEnabled() &&
            hasNotShownBefore() &&
            isActivityValid(activity)

    private fun isPickerEnabled(): Boolean =
        duckAiFeatureState.showAIChatAddressBarOptionChoiceScreen.value.also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it isPickerEnabled" }
        }

    private fun isDuckAiEnabled(): Boolean =
        duckChat.isEnabled().also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it isDuckAiEnabled" }
        }

    private suspend fun isOnboardingCompleted(): Boolean =
        (userStageStore.getUserAppStage() == AppStage.ESTABLISHED).also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it isOnboardingCompleted" }
        }

    private suspend fun isInputScreenNeverEnabled(): Boolean =
        (!duckChat.isInputScreenEverEnabled()).also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it isInputScreenNeverEnabled" }
        }

    private suspend fun hasNotShownBefore(): Boolean =
        (!newAddressBarPickerDataStore.wasShown()).also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it hasNotShownBefore" }
        }

    private fun isActivityValid(activity: Activity): Boolean =
        (!activity.isFinishing && !activity.isDestroyed).also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it isActivityValid" }
        }

    private fun showChoiceScreenDialog(activity: DuckDuckGoActivity) {
        newAddressBarPickerBottomSheetDialogFactory.create(
            context = activity,
            isLightMode = appTheme.isLightModeEnabled(),
            callback =
            object : NewAddressBarCallback {
                override fun onDisplayed() {
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_COUNT)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_DAILY, type = Pixel.PixelType.Daily())
                    appCoroutineScope.launch {
                        // After being shown MAX_DISPLAY_COUNT times without a choice, stop offering it.
                        if (newAddressBarPickerDataStore.incrementDisplayCount() >= MAX_DISPLAY_COUNT) {
                            newAddressBarPickerDataStore.setAsShown()
                        }
                    }
                }

                override fun onConfirmed(searchAndAiSelected: Boolean) {
                    appCoroutineScope.launch {
                        newAddressBarPickerDataStore.setAsShown()
                        if (searchAndAiSelected) {
                            duckChat.setInputScreenUserSetting(true)
                            duckChat.onAddressBarPickerDuckAiSelected()
                        }
                    }
                    val params = mapOf(SELECTION_PARAM to if (searchAndAiSelected) SELECTION_SEARCH_AND_AI else SELECTION_SEARCH_ONLY)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_COUNT, parameters = params)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_DAILY, parameters = params, type = Pixel.PixelType.Daily())
                }
            },
        ).show()
    }

    private companion object {
        private const val MAX_DISPLAY_COUNT = 2
        private const val SELECTION_PARAM = "selection"
        private const val SELECTION_SEARCH_AND_AI = "search_and_ai"
        private const val SELECTION_SEARCH_ONLY = "search_only"
    }
}
