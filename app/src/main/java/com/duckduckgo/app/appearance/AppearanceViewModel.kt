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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AppearanceViewModel @Inject constructor(
    private val themingDataStore: ThemingDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT,
        val appIcon: AppIcon = AppIcon.DEFAULT,
    )

    sealed class Command {
        data class LaunchThemeSettings(val theme: DuckDuckGoTheme) : Command()
        object LaunchAppIcon : Command()
        object UpdateTheme : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onStartActivityCalled() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    theme = themingDataStore.theme,
                    appIcon = settingsDataStore.appIcon,
                ),
            )
        }
    }

    fun userRequestedToChangeTheme() {
        viewModelScope.launch { command.send(Command.LaunchThemeSettings(viewState.value.theme)) }
        pixel.fire(AppPixelName.SETTINGS_THEME_OPENED)
    }

    fun userRequestedToChangeIcon() {
        viewModelScope.launch { command.send(Command.LaunchAppIcon) }
        pixel.fire(AppPixelName.SETTINGS_APP_ICON_PRESSED)
    }

    fun onThemeSelected(selectedTheme: DuckDuckGoTheme) {
        Timber.d("User toggled theme, theme to set: $selectedTheme")
        if (themingDataStore.isCurrentlySelected(selectedTheme)) {
            Timber.d("User selected same theme they've already set: $selectedTheme; no need to do anything else")
            return
        }
        themingDataStore.theme = selectedTheme
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(theme = selectedTheme))
            command.send(Command.UpdateTheme)
        }

        val pixelName =
            when (selectedTheme) {
                DuckDuckGoTheme.LIGHT -> AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT
                DuckDuckGoTheme.DARK -> AppPixelName.SETTINGS_THEME_TOGGLED_DARK
                DuckDuckGoTheme.SYSTEM_DEFAULT -> AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT
            }
        pixel.fire(pixelName)
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
