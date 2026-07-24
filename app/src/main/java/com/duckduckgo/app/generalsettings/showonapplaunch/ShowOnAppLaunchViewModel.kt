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
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
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
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val urlConverter: UrlConverter,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val idleThresholdResolver: IdleThresholdResolver,
) : ViewModel() {

    data class ViewState(
        val selectedOption: ShowOnAppLaunchOption,
        val specificPageUrl: String,
        val showNTPAfterIdleReturn: Boolean = false,
        val selectedIdleThresholdSeconds: Long = FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS,
        val idleThresholdOptions: List<Long> = FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_OPTIONS,
        val returnToLastTabEnabled: Boolean = true,
    )

    sealed class Command {
        data class ShowTimeoutDialog(val options: List<Long>, val currentSelection: Long) : Command()
    }

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow().filterNotNull()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    private val userSelectedThreshold = MutableStateFlow(settingsDataStore.userSelectedIdleThresholdSeconds)

    init {
        observeShowOnAppLaunchOptionChanges()
    }

    private fun observeShowOnAppLaunchOptionChanges() {
        combine(
            showOnAppLaunchOptionDataStore.optionFlow,
            showOnAppLaunchOptionDataStore.specificPageUrlFlow,
            androidBrowserConfigFeature.showNTPAfterIdleReturn().enabled(),
            userSelectedThreshold,
            ntpAfterIdleManager.returnToLastTabEnabled,
        ) { option, specificPageUrl, showNTPAfterIdleReturn, userThreshold, returnToLastTabEnabled ->
            _viewState.value = ViewState(
                selectedOption = option,
                specificPageUrl = specificPageUrl,
                showNTPAfterIdleReturn = showNTPAfterIdleReturn,
                selectedIdleThresholdSeconds = idleThresholdResolver.effectiveThresholdSeconds(userThreshold),
                returnToLastTabEnabled = returnToLastTabEnabled,
            )
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onShowOnAppLaunchOptionChanged(option: ShowOnAppLaunchOption) {
        viewModelScope.launch(dispatcherProvider.io()) {
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(option)
            val (countPixel, dailyPixel) = when (option) {
                ShowOnAppLaunchOption.LastOpenedTab ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB to ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB_DAILY
                ShowOnAppLaunchOption.NewTabPage ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE to ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY
                is ShowOnAppLaunchOption.SpecificPage ->
                    ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE to ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY
            }
            pixel.fire(countPixel, type = Count)
            pixel.fire(dailyPixel, type = Daily())
        }
    }

    fun setSpecificPageUrl(url: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val convertedUrl = urlConverter.convertUrl(url)
            showOnAppLaunchOptionDataStore.setSpecificPageUrl(convertedUrl)
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
            settingsDataStore.userSelectedIdleThresholdSeconds = seconds
            userSelectedThreshold.value = seconds
            pixel.fire(SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED, mapOf("selectedSeconds" to seconds.toString()))
            ntpAfterIdleManager.onIdleTimeoutSelected(seconds)
        }
    }

    fun onReturnToLastTabToggled(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            ntpAfterIdleManager.setReturnToLastTabEnabled(enabled)
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
