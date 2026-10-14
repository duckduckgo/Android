/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.browser.autofill.SystemAutofillEngagement
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.wideevents.BrowserInteractionsPlugin
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.isHttpOrHttps
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

enum class AfterIdleTreatment {
    NTP,
    LUT,
}

data class ShowOnAppLaunchResult(
    val destinationUrl: String?,
    val treatment: AfterIdleTreatment?,
)

interface ShowOnAppLaunchOptionHandler {
    suspend fun handleAfterInactivityOption(wasIdle: Boolean, currentMode: BrowserMode): ShowOnAppLaunchResult
    suspend fun handleAppLaunchOption(currentMode: BrowserMode): ShowOnAppLaunchResult
    suspend fun handleResolvedUrlStorage(
        currentUrl: String?,
        isRootOfTab: Boolean,
        tabId: String,
    )
}

@ContributesBinding(AppScope::class)
class ShowOnAppLaunchOptionHandlerImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val settingsDataStore: SettingsDataStore,
    private val systemAutofillEngagement: SystemAutofillEngagement,
    private val browserInteractionsPlugins: PluginPoint<BrowserInteractionsPlugin>,
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
) : ShowOnAppLaunchOptionHandler {

    override suspend fun handleAfterInactivityOption(wasIdle: Boolean, currentMode: BrowserMode): ShowOnAppLaunchResult {
        logcat { "FirstScreen: Inactivity Timer passed" }
        return applyShowOnAppLaunchOption(afterIdle = wasIdle, currentMode = currentMode)
    }

    override suspend fun handleAppLaunchOption(currentMode: BrowserMode): ShowOnAppLaunchResult =
        applyShowOnAppLaunchOption(afterIdle = false, currentMode = currentMode)

    private suspend fun applyShowOnAppLaunchOption(
        afterIdle: Boolean,
        currentMode: BrowserMode,
    ): ShowOnAppLaunchResult {
        val option = showOnAppLaunchOptionDataStore.optionFlow.first()
        logcat { "FirstScreen: showing $option on app launch" }

        return when (option) {
            LastOpenedTab -> handleLastOpenedTab(afterIdle, currentMode)
            NewTabPage -> handleNewTabPage(afterIdle, currentMode)
            is SpecificPage -> handleSpecificPage(option, afterIdle, currentMode)
        }
    }

    private suspend fun handleLastOpenedTab(
        afterIdle: Boolean,
        currentMode: BrowserMode,
    ): ShowOnAppLaunchResult {
        val selectedUrl = tabRepositoryProvider.forMode(currentMode).getSelectedTab()?.url
        val isAfterIdleLut = afterIdle && currentMode == BrowserMode.REGULAR && !selectedUrl.isNullOrBlank()
        val treatment = if (isAfterIdleLut) {
            browserInteractionsPlugins.getPlugins().forEach { it.onLutShownAfterIdle() }
            AfterIdleTreatment.LUT
        } else {
            null
        }
        return ShowOnAppLaunchResult(destinationUrl = selectedUrl, treatment = treatment)
    }

    private suspend fun handleNewTabPage(
        afterIdle: Boolean,
        currentMode: BrowserMode,
    ): ShowOnAppLaunchResult {
        val tabRepository = tabRepositoryProvider.forMode(currentMode)
        val selectedTab = tabRepository.getSelectedTab()
        val shouldAddNewTab = selectedTab == null || !selectedTab.url.isNullOrBlank()
        val isAfterIdleNtp = shouldAddNewTab && afterIdle && currentMode == BrowserMode.REGULAR
        val treatment = if (isAfterIdleNtp) {
            // Set pendingAfterIdle before adding the tab so the selected-tab emission consumes it for this NTP.
            ntpAfterIdleManager.onIdleReturnTriggered()
            notifyAutofillIdleReturn("new_tab_page")
            AfterIdleTreatment.NTP
        } else {
            null
        }

        if (shouldAddNewTab) {
            tabRepository.add()
        }
        // An existing NTP keeps its prior classification; triggering here would leak treatment to the next NTP.
        return ShowOnAppLaunchResult(destinationUrl = null, treatment = treatment)
    }

    private suspend fun handleSpecificPage(
        option: SpecificPage,
        afterIdle: Boolean,
        currentMode: BrowserMode,
    ): ShowOnAppLaunchResult {
        if (currentMode != BrowserMode.REGULAR) {
            val selectedUrl = tabRepositoryProvider.forMode(currentMode).getSelectedTab()?.url
            return ShowOnAppLaunchResult(destinationUrl = selectedUrl, treatment = null)
        }
        if (afterIdle) {
            notifyAutofillIdleReturn("specific_page")
        }
        return ShowOnAppLaunchResult(
            destinationUrl = handleSpecificPageOption(option),
            treatment = null,
        )
    }

    override suspend fun handleResolvedUrlStorage(
        currentUrl: String?,
        isRootOfTab: Boolean,
        tabId: String,
    ) {
        withContext(dispatchers.io()) {
            if (currentUrl != null && isRootOfTab && tabId == showOnAppLaunchOptionDataStore.showOnAppLaunchTabId) {
                showOnAppLaunchOptionDataStore.setResolvedPageUrl(currentUrl)
            }
        }
    }

    private fun notifyAutofillIdleReturn(optionName: String) {
        if (settingsDataStore.userSelectedIdleThresholdSeconds == 0L) {
            systemAutofillEngagement.setIdleReturnTriggered(optionName)
        }
    }

    private suspend fun handleSpecificPageOption(option: SpecificPage): String {
        val tabRepository = tabRepositoryProvider.forMode(BrowserMode.REGULAR)
        val urls = listOfNotNull(option.url, option.resolvedUrl).map { stripIfHttpOrHttps(it.toUri()) }

        val existingTab = tabRepository.flowTabs.first().findLast { tab ->
            tab.url?.takeIf { it.isNotBlank() }?.let { stripIfHttpOrHttps(it.toUri()) in urls } == true
        }

        return if (existingTab != null) {
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchTabId(existingTab.tabId)
            tabRepository.select(existingTab.tabId)
            existingTab.url ?: option.url
        } else {
            val tabId = tabRepository.add(url = option.url)
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchTabId(tabId)
            option.url
        }
    }

    private fun stripIfHttpOrHttps(uri: Uri): String {
        return if (uri.isHttpOrHttps) {
            stripUri(uri)
        } else {
            uri.toString()
        }
    }

    private fun stripUri(uri: Uri): String = uri.run {
        val authority = uri.authority?.removePrefix("www.")
        uri.buildUpon()
            .scheme(null)
            .authority(authority)
            .toString()
            .replaceFirst("//", "")
    }
}
