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
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.promptscoordinator.api.PromptExposureReporter
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

interface NewAddressBarPickerManager {
    suspend fun showChoiceScreen(activity: DuckDuckGoActivity)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewAddressBarPickerManager @Inject constructor(
    private val duckChat: DuckChat,
    private val userStageStore: UserStageStore,
    private val newAddressBarPickerDataStore: NewAddressBarPickerDataStore,
    private val newAddressBarPickerBottomSheetDialogFactory: NewAddressBarPickerBottomSheetDialogFactory,
    private val pixel: Pixel,
    private val appTheme: AppTheme,
    private val promptExposureReporter: PromptExposureReporter,
    private val duckAiToggleMigrationFeature: DuckAiToggleMigrationFeature,
    private val duckAiToggleMigrationGate: DuckAiToggleMigrationGate,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : NewAddressBarPickerManager {
    private val showChoiceScreenMutex = Mutex()
    private var currentDialog: BottomSheetDialog? = null

    override suspend fun showChoiceScreen(activity: DuckDuckGoActivity) {
        showChoiceScreenMutex.withLock {
            if (validate(activity)) {
                logcat(DEBUG) { "NewAddressBarPickerManager: All conditions met, showing choice screen" }
                withContext(dispatchers.main()) {
                    // The sheet is non-cancelable, so don't present a second one while one is already showing
                    // (showHome() can re-fire from browser/SSL error state clearing while still on the new tab).
                    if (currentDialog?.isShowing != true) {
                        showChoiceScreenDialog(activity)
                    }
                }
            }
        }
    }

    // The gate comes after the checks the migration can't change, so users who can never see the picker don't wait, and
    // before the ones it can, so they read its outcome. The activity is checked last because the wait can outlive it.
    private suspend fun validate(activity: Activity): Boolean =
        isDuckAiEnabled() &&
            isOnboardingCompleted() &&
            hasMigrationDecided() &&
            isInputScreenNeverEnabled() &&
            hasNotShownBefore() &&
            isActivityValid(activity)

    // The migration may be about to flip this user, so let it decide first. If it hasn't by the timeout, ask another time.
    private suspend fun hasMigrationDecided(): Boolean =
        duckAiToggleMigrationGate.awaitOpen(MIGRATION_GATE_TIMEOUT).also {
            logcat(DEBUG) { "NewAddressBarPickerManager: $it hasMigrationDecided" }
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
        currentDialog = newAddressBarPickerBottomSheetDialogFactory.create(
            context = activity,
            isLightMode = appTheme.isLightModeEnabled(),
            callback =
            object : NewAddressBarCallback {
                override fun onDisplayed() {
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_COUNT)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_DAILY, type = Pixel.PixelType.Daily())
                    promptExposureReporter.reportPromptShown(NEW_ADDRESS_BAR_PICKER_PROMPT_ID)
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
                        } else if (isSearchOnlyWriteEnabled()) {
                            // Record the explicit "no" like a Settings change, so nothing later mistakes it for "never asked".
                            duckChat.setInputScreenUserSetting(false)
                        }
                    }
                    val params = mapOf(SELECTION_PARAM to if (searchAndAiSelected) SELECTION_SEARCH_AND_AI else SELECTION_SEARCH_ONLY)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_COUNT, parameters = params)
                    pixel.fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_DAILY, parameters = params, type = Pixel.PixelType.Daily())
                }
            },
        ).apply {
            setOnDismissListener { currentDialog = null }
            show()
        }
    }

    private suspend fun isSearchOnlyWriteEnabled(): Boolean = withContext(dispatchers.io()) {
        duckAiToggleMigrationFeature.pickerSearchOnlyWritesSetting().isEnabled()
    }

    private companion object {
        private const val MAX_DISPLAY_COUNT = 2
        private val MIGRATION_GATE_TIMEOUT = 2.seconds
        private const val SELECTION_PARAM = "selection"
        private const val SELECTION_SEARCH_AND_AI = "search_and_ai"
        private const val SELECTION_SEARCH_ONLY = "search_only"
        private const val NEW_ADDRESS_BAR_PICKER_PROMPT_ID = "new_address_bar_picker"
    }
}
