/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.ThemeFeature.ThemeToggle
import timber.log.Timber
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager
) : ViewModel() {

    data class ViewState(
        val loading: Boolean = true,
        val version: String = "",
        val showThemeToggle: Boolean = false,
        val lightThemeEnabled: Boolean = false,
        val autoCompleteSuggestionsEnabled: Boolean = true,
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false
    )

    private lateinit var currentViewState: ViewState

    sealed class Command {
        object LaunchFeedback : Command()
        object UpdateTheme : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        currentViewState = ViewState()
        value = currentViewState
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun start() {

        val defaultBrowserAlready = defaultWebBrowserCapability.isCurrentlyConfiguredAsDefaultBrowser()
        val variant = variantManager.getVariant()
        val isLightTheme = settingsDataStore.theme == DuckDuckGoTheme.LIGHT

        viewState.value = currentViewState.copy(
            loading = false,
            showThemeToggle = variant.hasFeature(ThemeToggle),
            lightThemeEnabled = isLightTheme,
            autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
            isAppDefaultBrowser = defaultBrowserAlready,
            showDefaultBrowserSetting = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration(),
            version = obtainVersion(variant.key)
        )
    }

    fun userRequestedToSendFeedback() {
        command.value = Command.LaunchFeedback
    }

    fun onLightThemeToggled(enabled: Boolean) {
        Timber.i("User toggled light theme, is now enabled: $enabled")
        settingsDataStore.theme = if (enabled) DuckDuckGoTheme.LIGHT else DuckDuckGoTheme.DARK
        command.value = Command.UpdateTheme
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
    }

    private fun obtainVersion(variantKey: String): String {
        val formattedVariantKey = if (variantKey.isBlank()) " " else " $variantKey "
        return "${BuildConfig.VERSION_NAME}$formattedVariantKey(${BuildConfig.VERSION_CODE})"
    }
}