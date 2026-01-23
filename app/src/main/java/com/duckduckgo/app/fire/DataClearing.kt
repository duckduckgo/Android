/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.fire

import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Implementation that provides granular data clearing capabilities for both manual and automatic clearing.
 * This uses the FireDataStore to determine which data to clear based on user preferences.
 */
@ContributesBinding(
    scope = AppScope::class,
    boundType = ManualDataClearing::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AutomaticDataClearing::class,
)
@SingleInstanceIn(AppScope::class)
class DataClearing @Inject constructor(
    private val fireDataStore: FireDataStore,
    private val clearDataAction: ClearDataAction,
    private val settingsDataStore: SettingsDataStore,
    private val dataClearerTimeKeeper: BackgroundTimeKeeper,
    private val webViewProfileManager: WebViewProfileManager,
) : ManualDataClearing, AutomaticDataClearing {

    override suspend fun clearDataUsingManualFireOptions(
        shouldRestartIfRequired: Boolean,
        wasAppUsedSinceLastClear: Boolean,
    ) {
        if (webViewProfileManager.isProfileSwitchingAvailable()) {
            clearDataWithProfileSwitch(shouldRestartIfRequired)
        } else {
            clearDataWithProcessRestart(shouldRestartIfRequired)
        }
        clearDataAction.setAppUsedSinceLastClearFlag(wasAppUsedSinceLastClear)
    }

    private suspend fun clearDataWithProfileSwitch(shouldRestartIfRequired: Boolean) {
        val options = fireDataStore.getManualClearOptions()
        val shouldDeleteTabs = options.contains(FireClearOption.TABS)

        // delete data without tabs first (tabs will be handled after profile switch if needed)
        performGranularClear(
            options = options - FireClearOption.TABS,
            shouldFireDataClearPixel = true,
        )

        val wasProfileSwitched = webViewProfileManager.switchToNewProfile()
        if (wasProfileSwitched) {
            logcat { "Profile switch successful, resetting tabs" }
            if (shouldDeleteTabs) {
                clearDataAction.clearTabsOnly()
            } else {
                clearDataAction.resetTabsForProfileSwitch()
            }
        } else {
            logcat(WARN) { "Profile switching failed, fall back to process restart" }

            clearDataWithProcessRestart(shouldRestartIfRequired)
        }
    }

    private suspend fun clearDataWithProcessRestart(shouldRestartIfRequired: Boolean) {
        val options = fireDataStore.getManualClearOptions()

        performGranularClear(
            options = options,
            shouldFireDataClearPixel = true,
        )

        val wasDataCleared = options.contains(FireClearOption.DATA) ||
            options.contains(FireClearOption.DUCKAI_CHATS)
        if (shouldRestartIfRequired && wasDataCleared) {
            clearDataAction.killAndRestartProcess(notifyDataCleared = false)
        }
    }

    override suspend fun clearDataUsingAutomaticFireOptions(killProcessIfNeeded: Boolean): Boolean {
        val options = fireDataStore.getAutomaticClearOptions()
        performGranularClear(
            options = options,
            shouldFireDataClearPixel = false,
        )

        clearDataAction.setAppUsedSinceLastClearFlag(!killProcessIfNeeded)

        val wasDataCleared = options.contains(FireClearOption.DATA) || options.contains(FireClearOption.DUCKAI_CHATS)
        if (killProcessIfNeeded && wasDataCleared) {
            clearDataAction.killProcess()
            return false
        } else {
            return wasDataCleared
        }
    }

    override suspend fun shouldClearDataAutomatically(
        isFreshAppLaunch: Boolean,
        appUsedSinceLastClear: Boolean,
        appIconChanged: Boolean,
    ): Boolean {
        val clearWhenOption = fireDataStore.getAutomaticallyClearWhenOption()

        logcat { "Determining if data should be cleared for option $clearWhenOption" }

        if (fireDataStore.getAutomaticClearOptions().isEmpty()) {
            logcat { "No automatic clear options selected; will not clear data" }
            return false
        }

        if (!appUsedSinceLastClear) {
            logcat { "App hasn't been used since last clear; no need to clear again" }
            return false
        }

        logcat { "App has been used since last clear" }

        if (isFreshAppLaunch) {
            logcat { "This is a fresh app launch, so will clear the data" }
            return true
        }

        if (appIconChanged) {
            logcat { "No data will be cleared as the app icon was just changed" }
            return false
        }

        if (clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
            logcat { "This is NOT a fresh app launch, and the configuration is for app exit only. Not clearing the data" }
            return false
        }
        if (!settingsDataStore.hasBackgroundTimestampRecorded()) {
            logcat { "No background timestamp recorded; will not clear the data" }
            logcat(WARN) { "No background timestamp recorded; will not clear the data" }
            return false
        }

        val enoughTimePassed = dataClearerTimeKeeper.hasEnoughTimeElapsed(
            backgroundedTimestamp = settingsDataStore.appBackgroundedTimestamp,
            clearWhenOption = clearWhenOption,
        )
        logcat { "Has enough time passed to trigger the data clear? $enoughTimePassed" }

        return enoughTimePassed
    }

    override suspend fun isAutomaticDataClearingOptionSelected(): Boolean {
        return fireDataStore.getAutomaticClearOptions().isNotEmpty()
    }

    /**
     * Performs granular data clearing based on the provided options
     * @return true if process needs to be restarted
     */
    private suspend fun performGranularClear(
        options: Set<FireClearOption>,
        shouldFireDataClearPixel: Boolean,
    ) {
        logcat { "Performing granular clear with options: $options" }

        val shouldClearTabs = FireClearOption.TABS in options
        val shouldClearData = FireClearOption.DATA in options
        val shouldClearDuckAiChats = FireClearOption.DUCKAI_CHATS in options

        if (shouldClearTabs) {
            clearDataAction.clearTabsOnly()
        }

        if (shouldClearData) {
            clearDataAction.clearBrowserDataOnly(shouldFireDataClearPixel)
        }

        if (shouldClearDuckAiChats) {
            clearDataAction.clearDuckAiChatsOnly()
        }

        logcat { "Granular clear completed" }
    }
}
