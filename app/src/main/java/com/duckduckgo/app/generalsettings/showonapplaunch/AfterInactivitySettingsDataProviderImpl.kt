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
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import com.duckduckgo.settings.api.AfterInactivitySettingsDataProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val pixel: Pixel,
) : AfterInactivitySettingsDataProvider {

    override val settings: Flow<AfterInactivitySettings> = combine(
        showOnAppLaunchOptionDataStore.optionFlow,
        settingsDataStore.userSelectedIdleThresholdSecondsFlow,
        ntpAfterIdleManager.returnToLastTabEnabled,
        androidBrowserConfigFeature.showNTPAfterIdleReturn().enabled(),
    ) { option, selectedSeconds, shortcutEnabled, _ ->
        val effectiveSeconds = idleThresholdResolver.effectiveThresholdSeconds(selectedSeconds)
        option.toSettings(effectiveSeconds, shortcutEnabled)
    }.distinctUntilChanged()

    override suspend fun setDestination(destination: AfterInactivityReturnDestination) {
        val currentOption = showOnAppLaunchOptionDataStore.optionFlow.first()
        val optionWasExplicitlySelected = showOnAppLaunchOptionDataStore.hasOptionSelected()
        val requestedOption = destination.toShowOnAppLaunchOption()

        showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(requestedOption)
        if (optionWasExplicitlySelected && currentOption.discriminator() != requestedOption.discriminator()) {
            pixel.fire(destination.countPixel(), type = Count)
            pixel.fire(destination.dailyPixel(), type = Daily())
        }

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
        pixel.fire(SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED, mapOf("selectedSeconds" to seconds.toString()))
        ntpAfterIdleManager.onIdleTimeoutSelected(seconds)
    }

    private suspend fun saveReturnToLastTabShortcut(enabled: Boolean) {
        ntpAfterIdleManager.setReturnToLastTabEnabled(enabled)
        if (enabled) {
            pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED, type = Count)
            pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED_DAILY, type = Daily())
        } else {
            pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED, type = Count)
            pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED_DAILY, type = Daily())
        }
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

    private fun ShowOnAppLaunchOption.discriminator(): Int = id

    private fun AfterInactivityReturnDestination.countPixel(): ShowOnAppLaunchPixelName = when (this) {
        AfterInactivityReturnDestination.LastUsedTab -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB
        is AfterInactivityReturnDestination.NewTabPage -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE
        is AfterInactivityReturnDestination.SpecificPage -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE
    }

    private fun AfterInactivityReturnDestination.dailyPixel(): ShowOnAppLaunchPixelName = when (this) {
        AfterInactivityReturnDestination.LastUsedTab -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB_DAILY
        is AfterInactivityReturnDestination.NewTabPage -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY
        is AfterInactivityReturnDestination.SpecificPage -> ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY
    }
}
