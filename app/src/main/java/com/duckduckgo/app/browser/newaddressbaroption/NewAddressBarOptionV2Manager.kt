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

interface NewAddressBarOptionV2Manager {
    suspend fun showChoiceScreen(activity: DuckDuckGoActivity)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewAddressBarOptionV2Manager @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckChat: DuckChat,
    private val userStageStore: UserStageStore,
    private val newAddressBarOptionV2DataStore: NewAddressBarOptionV2DataStore,
    private val newAddressBarOptionV2BottomSheetDialogFactory: NewAddressBarOptionV2BottomSheetDialogFactory,
    private val pixel: Pixel,
    private val appTheme: AppTheme,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : NewAddressBarOptionV2Manager {
    private val showChoiceScreenMutex = Mutex()

    override suspend fun showChoiceScreen(activity: DuckDuckGoActivity) {
        showChoiceScreenMutex.withLock {
            if (validate(activity)) {
                logcat(DEBUG) { "NewAddressBarOptionV2Manager: All conditions met, showing choice screen" }
                newAddressBarOptionV2DataStore.setAsShown()
                withContext(dispatchers.main()) {
                    showChoiceScreenDialog(activity)
                }
            }
        }
    }

    private suspend fun validate(activity: Activity): Boolean =
        isV2Enabled() &&
            isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            isInputScreenNeverEnabled() &&
            hasNotShownBefore() &&
            isActivityValid(activity)

    private fun isV2Enabled(): Boolean =
        duckAiFeatureState.showAIChatAddressBarOptionChoiceScreenV2.value.also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it isV2Enabled" }
        }

    private fun isDuckAiEnabled(): Boolean =
        duckChat.isEnabled().also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it isDuckAiEnabled" }
        }

    private suspend fun isOnboardingCompleted(): Boolean =
        (userStageStore.getUserAppStage() == AppStage.ESTABLISHED).also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it isOnboardingCompleted" }
        }

    private suspend fun isInputScreenNeverEnabled(): Boolean =
        (!duckChat.isInputScreenEverEnabled()).also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it isInputScreenNeverEnabled" }
        }

    private suspend fun hasNotShownBefore(): Boolean =
        (!newAddressBarOptionV2DataStore.wasShown()).also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it hasNotShownBefore" }
        }

    private fun isActivityValid(activity: Activity): Boolean =
        (!activity.isFinishing && !activity.isDestroyed).also {
            logcat(DEBUG) { "NewAddressBarOptionV2Manager: $it isActivityValid" }
        }

    private fun showChoiceScreenDialog(activity: DuckDuckGoActivity) {
        newAddressBarOptionV2BottomSheetDialogFactory.create(
            context = activity,
            isLightMode = appTheme.isLightModeEnabled(),
            callback =
            object : NewAddressBarV2Callback {
                override fun onDisplayed() {
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED)
                }

                override fun onConfirmed(searchAndAiSelected: Boolean) {
                    if (searchAndAiSelected) {
                        appCoroutineScope.launch {
                            duckChat.setInputScreenUserSetting(true)
                        }
                    }
                    val selectionParam = if (searchAndAiSelected) SELECTION_SEARCH_AND_AI else SELECTION_SEARCH_ONLY
                    pixel.fire(
                        AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED,
                        parameters = mapOf(SELECTION_PARAM to selectionParam),
                    )
                }
            },
        ).show()
    }

    private companion object {
        private const val SELECTION_PARAM = "selection"
        private const val SELECTION_SEARCH_AND_AI = "search_and_ai"
        private const val SELECTION_SEARCH_ONLY = "search_only"
    }
}
