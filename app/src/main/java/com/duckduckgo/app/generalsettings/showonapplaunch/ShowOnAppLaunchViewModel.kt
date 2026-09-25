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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import com.duckduckgo.settings.api.AfterInactivitySettingsDataProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ShowOnAppLaunchViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val afterInactivitySettingsDataProvider: AfterInactivitySettingsDataProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val selectedOption: ShowOnAppLaunchOption,
        val specificPageUrl: String,
        val showNTPAfterIdleReturn: Boolean = false,
        val selectedIdleThresholdSeconds: Long = FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS,
        val idleThresholdOptions: List<Long> = FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_OPTIONS,
        val returnToLastTabEnabled: Boolean = true,
        val showAfterInactivityTimeout: Boolean = false,
    )

    sealed class Command {
        data class ShowTimeoutDialog(val options: List<Long>, val currentSelection: Long) : Command()
    }

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow().filterNotNull()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        combine(
            afterInactivitySettingsDataProvider.settings,
            androidBrowserConfigFeature.showNTPAfterIdleReturn().enabled(),
        ) { settings, showNTPAfterIdleReturn ->
            _viewState.value = settings.toViewState(showNTPAfterIdleReturn)
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    private fun AfterInactivitySettings.toViewState(showNTPAfterIdleReturn: Boolean): ViewState {
        val effectiveTimeoutSeconds = when (this) {
            AfterInactivitySettings.LastUsedTab -> FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS
            is AfterInactivitySettings.NewTabPage -> effectiveTimeoutSeconds
            is AfterInactivitySettings.SpecificPage -> effectiveTimeoutSeconds
        }
        val specificPageUrl = (this as? AfterInactivitySettings.SpecificPage)?.url
            ?: AfterInactivityReturnDestination.SpecificPage().url
        return ViewState(
            selectedOption = toShowOnAppLaunchOption(),
            specificPageUrl = specificPageUrl,
            showNTPAfterIdleReturn = showNTPAfterIdleReturn,
            selectedIdleThresholdSeconds = effectiveTimeoutSeconds,
            returnToLastTabEnabled = (this as? AfterInactivitySettings.NewTabPage)?.returnToLastTabShortcutEnabled ?: true,
            showAfterInactivityTimeout = showNTPAfterIdleReturn && this !is AfterInactivitySettings.LastUsedTab,
        )
    }

    private fun AfterInactivitySettings.toShowOnAppLaunchOption(): ShowOnAppLaunchOption = when (this) {
        AfterInactivitySettings.LastUsedTab -> LastOpenedTab
        is AfterInactivitySettings.NewTabPage -> NewTabPage
        is AfterInactivitySettings.SpecificPage -> SpecificPage(url)
    }

    fun onShowOnAppLaunchOptionChanged(option: ShowOnAppLaunchOption) {
        viewModelScope.launch(dispatcherProvider.io()) {
            afterInactivitySettingsDataProvider.setDestination(option.toDestination())
            val (countPixel, dailyPixel) = when (option) {
                LastOpenedTab ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB to ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB_DAILY
                NewTabPage ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE to ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY
                is SpecificPage ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE to ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY
            }
            pixel.fire(countPixel, type = Count)
            pixel.fire(dailyPixel, type = Daily())
        }
    }

    private fun ShowOnAppLaunchOption.toDestination(): AfterInactivityReturnDestination = when (this) {
        LastOpenedTab -> AfterInactivityReturnDestination.LastUsedTab
        NewTabPage -> AfterInactivityReturnDestination.NewTabPage()
        is SpecificPage -> AfterInactivityReturnDestination.SpecificPage(url = url)
    }

    fun setSpecificPageUrl(url: String) {
        if (_viewState.value?.selectedOption !is SpecificPage) return
        viewModelScope.launch(dispatcherProvider.io()) {
            afterInactivitySettingsDataProvider.setDestination(AfterInactivityReturnDestination.SpecificPage(url = url))
        }
    }

    fun onTimeoutRowClicked() {
        val state = _viewState.value ?: return
        viewModelScope.launch {
            _commands.send(Command.ShowTimeoutDialog(state.idleThresholdOptions, state.selectedIdleThresholdSeconds))
        }
    }

    fun onTimeoutSelected(seconds: Long) {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val selectedOption = _viewState.value?.selectedOption) {
                is SpecificPage -> afterInactivitySettingsDataProvider.setDestination(
                    AfterInactivityReturnDestination.SpecificPage(url = selectedOption.url, selectedTimeoutSeconds = seconds),
                )
                NewTabPage -> afterInactivitySettingsDataProvider.setDestination(
                    AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = seconds),
                )
                LastOpenedTab, null -> return@launch
            }
            pixel.fire(SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED, mapOf("selectedSeconds" to seconds.toString()))
        }
    }

    fun onReturnToLastTabToggled(enabled: Boolean) {
        if (_viewState.value?.selectedOption != NewTabPage) return
        viewModelScope.launch(dispatcherProvider.io()) {
            afterInactivitySettingsDataProvider.setDestination(
                AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = enabled),
            )
            if (enabled) {
                pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED, type = Count)
                pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED_DAILY, type = Daily())
            } else {
                pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED, type = Count)
                pixel.fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED_DAILY, type = Daily())
            }
        }
    }
}
