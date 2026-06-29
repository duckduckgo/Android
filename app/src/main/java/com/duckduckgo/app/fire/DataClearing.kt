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

@file:SuppressLint("NoImplImportsInAppModule")

package com.duckduckgo.app.fire

import android.annotation.SuppressLint
import androidx.core.net.toUri
import com.duckduckgo.app.fire.promo.FireTabsPromos
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.ClearDataResult
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabAtomicOperations
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingTrigger
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.history.api.NavigationHistory
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
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
    private val duckAiFeatureState: DuckAiFeatureState,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val tabVisitedSitesRepository: TabVisitedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
    private val fireModeAvailability: FireModeAvailability,
    private val duckChat: DuckChat,
    private val contextualDataStore: DuckChatContextualDataStore,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val dataClearingTrigger: DataClearingTrigger,
    private val fireTabsPromos: FireTabsPromos,
) : ManualDataClearing, AutomaticDataClearing {

    override suspend fun clearSingleTabData(tabId: String, replaceCurrentTab: Boolean, browserMode: BrowserMode): ClearDataResult {
        suspend fun clearContextualChatDataIfNeeded(tabId: String) {
            val isDuckAiChatHistoryClearingEnabled = fireDataStore.getManualClearOptions()
                .contains(FireClearOption.DUCKAI_CHATS)

            if (isDuckAiChatHistoryClearingEnabled) {
                val contextualTabChatUrl = contextualDataStore.getTabChatUrl(tabId)
                clearDuckAiChatIfNeeded(contextualTabChatUrl, browserMode)

                contextualDataStore.clearTabChatUrl(tabId)
            }
        }

        logcat { "Performing single tab clear for tab: $tabId in mode: $browserMode" }

        val tabRepository = tabRepositoryProvider.forMode(browserMode)

        // Regular keeps the legacy ClearDataAction path.
        // Fire delegates to the site-data plugin.
        val clearDataResult = when (browserMode) {
            BrowserMode.REGULAR -> {
                val visitedSites = tabVisitedSitesRepository.getVisitedSites(tabId)
                clearDataAction.clearDataForSpecificDomains(visitedSites)
            }
            BrowserMode.FIRE -> {
                dataClearingTrigger.clearData(setOf(ClearableData.BrowserData.SingleForMode(tabId, BrowserMode.FIRE)))
                ClearDataResult.Success
            }
        }
        val tabUrl = tabRepository.getTab(tabId)?.url

        // Reset this tab's URL before dispatching the chat clear: the tabs-cleanup plugin matches
        // tabs by chatID, and we don't want this tab caught by that match — it stays open with a
        // new chat URL. Other tabs at the same chatID (duplicates) do get closed, which is desired.
        if (replaceCurrentTab) {
            val url = getNewTabUrl(tabUrl)
            (tabRepository as TabAtomicOperations).replaceTabWithNewTab(tabId, url)
        } else {
            tabRepository.deleteTabs(listOf(tabId))
        }

        clearDuckAiChatIfNeeded(tabUrl, browserMode)
        clearContextualChatDataIfNeeded(tabId)
        navigationHistory.removeHistoryForTab(tabId)

        if (browserMode == BrowserMode.REGULAR) {
            fireTabsPromos.onUserBurned()
        }

        logcat { "Single tab clear completed for tab: $tabId" }
        return clearDataResult
    }

    override suspend fun clearTabContextualChat(tabId: String, browserMode: BrowserMode): ClearDataResult {
        suspend fun deleteContextualChat(tabId: String) {
            val contextualTabChatUrl = contextualDataStore.getTabChatUrl(tabId)
            clearDuckAiChatIfNeeded(contextualTabChatUrl, browserMode)
        }

        logcat { "Performing contextual sheet clear for tab: $tabId" }

        deleteContextualChat(tabId)

        logcat { "Contextual sheet clear completed for tab: $tabId" }
        return ClearDataResult.Success
    }

    private suspend fun getNewTabUrl(tabUrl: String?): String? {
        val option = showOnAppLaunchOptionDataStore.optionFlow.firstOrNull()
        val isDuckChat = tabUrl?.toUri()?.let { duckChat.isDuckChatUrl(it) } == true
        return when {
            isDuckChat -> duckChat.getDuckChatUrl("", autoPrompt = false)
            option is ShowOnAppLaunchOption.SpecificPage -> option.url
            else -> null
        }
    }

    private suspend fun clearDuckAiChatIfNeeded(tabUrl: String?, browserMode: BrowserMode) {
        logcat { "clearDuckAiChatIfNeeded url=$tabUrl mode=$browserMode" }
        if (tabUrl == null) return
        dataClearingTrigger.clearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(tabUrl), browserMode)))
    }

    override suspend fun clearDataUsingManualFireOptions(
        shouldRestartIfRequired: Boolean,
        wasAppUsedSinceLastClear: Boolean,
        browserMode: BrowserMode,
    ) {
        when (browserMode) {
            BrowserMode.REGULAR -> {
                // Record the burn before clearing: clearRegularDataUsingManualFireOptions may kill and
                // restart the process, so anything after it would never run.
                fireTabsPromos.onUserBurned()
                performFireModeClear()
                clearRegularDataUsingManualFireOptions(shouldRestartIfRequired)
            }
            BrowserMode.FIRE -> {
                performFireModeClear()

                if (shouldRestartIfRequired) {
                    dataClearingWideEvent.finishSuccess() // If there is an open wide event, complete it before killing the process.
                    clearDataAction.killAndRestartProcess(notifyDataCleared = false)
                }
            }
        }

        clearDataAction.setAppUsedSinceLastClearFlag(wasAppUsedSinceLastClear)
    }

    private suspend fun clearRegularDataUsingManualFireOptions(
        shouldRestartIfRequired: Boolean,
    ) {
        val options = fireDataStore.getManualClearOptions()
        performRegularModeGranularClear(
            options = options,
            shouldFireDataClearPixel = true,
        )

        val wasDuckAiChatsCleared = options.contains(FireClearOption.DUCKAI_CHATS) &&
            duckAiFeatureState.showClearDuckAIChatHistory.value
        val wasDataCleared = options.contains(FireClearOption.DATA) || wasDuckAiChatsCleared
        if (shouldRestartIfRequired && wasDataCleared) {
            dataClearingWideEvent.finishSuccess() // If there is an open wide event, complete it before killing the process.
            clearDataAction.killAndRestartProcess(notifyDataCleared = false)
        }
    }

    override suspend fun clearDataUsingAutomaticFireOptions(killProcessIfNeeded: Boolean): Boolean {
        val options = fireDataStore.getAutomaticClearOptions()
        performRegularModeGranularClear(
            options = options,
            shouldFireDataClearPixel = false,
        )
        performFireModeClear()

        clearDataAction.setAppUsedSinceLastClearFlag(!killProcessIfNeeded)

        val wasDuckAiChatsCleared = options.contains(FireClearOption.DUCKAI_CHATS) &&
            duckAiFeatureState.showClearDuckAIChatHistory.value
        val wasDataCleared = options.contains(FireClearOption.DATA) || wasDuckAiChatsCleared
        if (killProcessIfNeeded && wasDataCleared) {
            dataClearingWideEvent.finishSuccess() // If there is an open wide event, complete it before killing the process.
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

    override suspend fun clearSelectedDuckAiChats(chatUrls: Set<String>, browserMode: BrowserMode) {
        if (chatUrls.isEmpty()) return
        if (!duckAiFeatureState.showClearDuckAIChatHistory.value) return
        dataClearingTrigger.clearData(setOf(ClearableData.DuckChats.SelectedForMode(chatUrls, browserMode)))
    }

    /**
     * Performs granular data clearing based on the provided options.
     *
     * The legacy [ClearDataAction] path clears Regular-mode data.
     */
    private suspend fun performRegularModeGranularClear(
        options: Set<FireClearOption>,
        shouldFireDataClearPixel: Boolean,
    ) {
        logcat { "Performing granular clear with options: $options" }

        val shouldClearTabs = FireClearOption.TABS in options
        val shouldClearData = FireClearOption.DATA in options
        val shouldClearDuckAiChats = FireClearOption.DUCKAI_CHATS in options &&
            duckAiFeatureState.showClearDuckAIChatHistory.value

        if (shouldClearTabs) clearDataAction.clearTabsOnly()
        if (shouldClearData) clearDataAction.clearBrowserDataOnly(shouldFireDataClearPixel)
        if (shouldClearDuckAiChats) {
            clearDataAction.clearDuckAiChatsOnly()
            dataClearingTrigger.clearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))
        }

        logcat { "Granular clear completed" }
    }

    /**
     * Performs the full Fire mode data clearing.
     */
    private suspend fun performFireModeClear() {
        logcat { "Performing Fire mode data clear" }

        if (fireModeAvailability.isAvailable()) {
            val fireTypes = setOf(
                ClearableData.Tabs.AllForMode(BrowserMode.FIRE),
                ClearableData.BrowserData.AllForMode(BrowserMode.FIRE),
                ClearableData.DuckChats.AllForMode(BrowserMode.FIRE),
            )
            dataClearingTrigger.clearData(fireTypes)
        }

        logcat { "Fire mode clear completed" }
    }
}
