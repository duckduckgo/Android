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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import com.duckduckgo.settings.api.AfterInactivitySettingsDataProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@ContributesBinding(AppScope::class, boundType = AfterInactivitySettingsDataProvider::class)
@SingleInstanceIn(AppScope::class)
class AfterInactivitySettingsDataProviderImpl @Inject constructor(
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val idleThresholdResolver: IdleThresholdResolver,
    private val urlConverter: UrlConverter,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
) : AfterInactivitySettingsDataProvider {

    private val userSelectedIdleThresholdSecondsFlow = MutableStateFlow(settingsDataStore.userSelectedIdleThresholdSeconds)

    override val settings: Flow<AfterInactivitySettings> = combine(
        showOnAppLaunchOptionDataStore.optionFlow,
        userSelectedIdleThresholdSecondsFlow,
        ntpAfterIdleManager.returnToLastTabEnabled,
        androidBrowserConfigFeature.showNTPAfterIdleReturn().enabled(),
    ) { option, selectedSeconds, shortcutEnabled, _ ->
        val effectiveSeconds = idleThresholdResolver.effectiveThresholdSeconds(selectedSeconds)
        option.toSettings(effectiveSeconds, shortcutEnabled)
    }.distinctUntilChanged()

    override suspend fun setDestination(destination: AfterInactivityReturnDestination) {
        val requestedOption = destination.toShowOnAppLaunchOption()

        showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(requestedOption)

        when (destination) {
            AfterInactivityReturnDestination.LastUsedTab -> Unit
            is AfterInactivityReturnDestination.NewTabPage -> {
                val selectedTimeoutSeconds = destination.selectedTimeoutSeconds
                val shortcutEnabled = destination.returnToLastTabShortcutEnabled
                if (selectedTimeoutSeconds != null) {
                    saveSelectedTimeout(selectedTimeoutSeconds)
                }
                if (shortcutEnabled != null) {
                    saveReturnToLastTabShortcut(shortcutEnabled)
                }
            }
            is AfterInactivityReturnDestination.SpecificPage -> {
                val selectedTimeoutSeconds = destination.selectedTimeoutSeconds
                if (selectedTimeoutSeconds != null) {
                    saveSelectedTimeout(selectedTimeoutSeconds)
                }
            }
        }
    }

    private suspend fun saveSelectedTimeout(seconds: Long) {
        settingsDataStore.userSelectedIdleThresholdSeconds = seconds
        userSelectedIdleThresholdSecondsFlow.value = seconds
        ntpAfterIdleManager.onIdleTimeoutSelected(seconds)
    }

    private suspend fun saveReturnToLastTabShortcut(enabled: Boolean) {
        ntpAfterIdleManager.setReturnToLastTabEnabled(enabled)
    }

    private fun ShowOnAppLaunchOption.toSettings(
        effectiveTimeoutSeconds: Long,
        returnToLastTabShortcutEnabled: Boolean,
    ): AfterInactivitySettings = when (this) {
        LastOpenedTab -> AfterInactivitySettings.LastUsedTab
        NewTabPage -> AfterInactivitySettings.NewTabPage(effectiveTimeoutSeconds, returnToLastTabShortcutEnabled)
        is SpecificPage -> AfterInactivitySettings.SpecificPage(url, effectiveTimeoutSeconds)
    }

    private fun AfterInactivityReturnDestination.toShowOnAppLaunchOption(): ShowOnAppLaunchOption = when (this) {
        AfterInactivityReturnDestination.LastUsedTab -> LastOpenedTab
        is AfterInactivityReturnDestination.NewTabPage -> NewTabPage
        is AfterInactivityReturnDestination.SpecificPage -> SpecificPage(urlConverter.convertUrl(url))
    }
}
