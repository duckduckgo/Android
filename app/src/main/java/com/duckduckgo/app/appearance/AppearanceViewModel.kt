/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewFeature
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.omnibar.ChangeOmnibarPositionFeature
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_DARK
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.common.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AppearanceViewModel @Inject constructor(
    private val themingDataStore: ThemingDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
    private val changeOmnibarPositionFeature: ChangeOmnibarPositionFeature,
) : ViewModel() {

    data class ViewState(
        val appIcon: AppIcon = AppIcon.DEFAULT,
        val forceDarkModeEnabled: Boolean = false,
        val canForceDarkMode: Boolean = false,
        val supportsForceDarkMode: Boolean = true,
        val omnibarPosition: OmnibarPosition = OmnibarPosition.TOP,
        val isOmnibarPositionFeatureEnabled: Boolean = true,
    )

    sealed class Command {
        data object LaunchAppIcon : Command()
        data class LaunchOmnibarPositionSettings(val position: OmnibarPosition) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch {
            viewState.update {
                currentViewState().copy(
                    appIcon = settingsDataStore.appIcon,
                    forceDarkModeEnabled = settingsDataStore.experimentalWebsiteDarkMode,
                    canForceDarkMode = canForceDarkMode(),
                    supportsForceDarkMode = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING),
                    omnibarPosition = settingsDataStore.omnibarPosition,
                    isOmnibarPositionFeatureEnabled = changeOmnibarPositionFeature.self().isEnabled(),
                )
            }
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    private fun canForceDarkMode(): Boolean {
        return themingDataStore.theme != DuckDuckGoTheme.LIGHT
    }

    fun userRequestedToChangeIcon() {
        viewModelScope.launch { command.send(Command.LaunchAppIcon) }
        pixel.fire(AppPixelName.SETTINGS_APP_ICON_PRESSED)
    }

    fun userRequestedToChangeAddressBarPosition() {
        viewModelScope.launch { command.send(Command.LaunchOmnibarPositionSettings(viewState.value.omnibarPosition)) }
        pixel.fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_PRESSED)
    }

    fun onOmnibarPositionUpdated(position: OmnibarPosition) {
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.omnibarPosition = position
            viewState.update { currentViewState().copy(omnibarPosition = position) }

            when (position) {
                OmnibarPosition.TOP -> pixel.fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_TOP)
                OmnibarPosition.BOTTOM -> pixel.fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_BOTTOM)
            }
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onForceDarkModeSettingChanged(checked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (checked) {
                pixel.fire(AppPixelName.FORCE_DARK_MODE_ENABLED)
            } else {
                pixel.fire(AppPixelName.FORCE_DARK_MODE_DISABLED)
            }
            settingsDataStore.experimentalWebsiteDarkMode = checked
        }
    }
}
