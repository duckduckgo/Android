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

package com.duckduckgo.app.firebutton

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class FireButtonViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val fireAnimationLoader: FireAnimationLoader,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val automaticallyClearData: AutomaticallyClearData = AutomaticallyClearData(
            ClearWhatOption.CLEAR_NONE,
            ClearWhenOption.APP_EXIT_ONLY,
        ),
        val selectedFireAnimation: FireAnimation = FireAnimation.HeroFire,
    )

    data class AutomaticallyClearData(
        val clearWhatOption: ClearWhatOption,
        val clearWhenOption: ClearWhenOption,
        val clearWhenOptionEnabled: Boolean = true,
    )

    sealed class Command {
        object LaunchFireproofWebsites : Command()
        data class ShowClearWhatDialog(val option: ClearWhatOption) : Command()
        data class ShowClearWhenDialog(val option: ClearWhenOption) : Command()
        data class LaunchFireAnimationSettings(val animation: FireAnimation) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun viewState(): Flow<ViewState> = viewState.onStart {
        val automaticallyClearWhat = settingsDataStore.automaticallyClearWhatOption
        val automaticallyClearWhen = settingsDataStore.automaticallyClearWhenOption
        val automaticallyClearWhenEnabled = isAutomaticallyClearingDataWhenSettingEnabled(automaticallyClearWhat)

        viewModelScope.launch(dispatcherProvider.io()) {
            viewState.emit(
                ViewState(
                    automaticallyClearData = AutomaticallyClearData(
                        automaticallyClearWhat,
                        automaticallyClearWhen,
                        automaticallyClearWhenEnabled,
                    ),
                    selectedFireAnimation = settingsDataStore.selectedFireAnimation,
                ),
            )
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onFireproofWebsitesClicked() {
        viewModelScope.launch(dispatcherProvider.io()) { command.send(Command.LaunchFireproofWebsites) }
        pixel.fire(AppPixelName.SETTINGS_FIREPROOF_WEBSITES_PRESSED)
    }

    fun onAutomaticallyClearWhatClicked() {
        viewModelScope.launch(dispatcherProvider.io()) {
            command.send(
                Command.ShowClearWhatDialog(viewState.value.automaticallyClearData.clearWhatOption),
            )
        }
        pixel.fire(AppPixelName.SETTINGS_AUTOMATICALLY_CLEAR_WHAT_PRESSED)
    }

    fun onAutomaticallyClearWhenClicked() {
        viewModelScope.launch(dispatcherProvider.io()) {
            command.send(
                Command.ShowClearWhenDialog(viewState.value.automaticallyClearData.clearWhenOption),
            )
        }
        pixel.fire(AppPixelName.SETTINGS_AUTOMATICALLY_CLEAR_WHEN_PRESSED)
    }

    fun onAutomaticallyWhatOptionSelected(clearWhatNewSetting: ClearWhatOption) {
        if (settingsDataStore.isCurrentlySelected(clearWhatNewSetting)) {
            Timber.v("User selected same thing they already have set: $clearWhatNewSetting; no need to do anything else")
            return
        }

        pixel.fire(clearWhatNewSetting.pixelEvent())

        settingsDataStore.automaticallyClearWhatOption = clearWhatNewSetting

        viewModelScope.launch(dispatcherProvider.io()) {
            viewState.emit(
                currentViewState().copy(
                    automaticallyClearData = AutomaticallyClearData(
                        clearWhatOption = clearWhatNewSetting,
                        clearWhenOption = settingsDataStore.automaticallyClearWhenOption,
                        clearWhenOptionEnabled = isAutomaticallyClearingDataWhenSettingEnabled(clearWhatNewSetting),
                    ),
                ),
            )
        }
    }

    fun onAutomaticallyWhenOptionSelected(clearWhenNewSetting: ClearWhenOption) {
        if (settingsDataStore.isCurrentlySelected(clearWhenNewSetting)) {
            Timber.v("User selected same thing they already have set: $clearWhenNewSetting; no need to do anything else")
            return
        }

        clearWhenNewSetting.pixelEvent()?.let {
            pixel.fire(it)
        }

        settingsDataStore.automaticallyClearWhenOption = clearWhenNewSetting
        viewModelScope.launch(dispatcherProvider.io()) {
            viewState.emit(
                currentViewState().copy(
                    automaticallyClearData = AutomaticallyClearData(
                        settingsDataStore.automaticallyClearWhatOption,
                        clearWhenNewSetting,
                    ),
                ),
            )
        }
    }

    fun userRequestedToChangeFireAnimation() {
        viewModelScope.launch(dispatcherProvider.io()) { command.send(Command.LaunchFireAnimationSettings(viewState.value.selectedFireAnimation)) }
        pixel.fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    fun onFireAnimationSelected(selectedFireAnimation: FireAnimation) {
        if (settingsDataStore.isCurrentlySelected(selectedFireAnimation)) {
            Timber.v("User selected same thing they already have set: $selectedFireAnimation; no need to do anything else")
            return
        }
        settingsDataStore.selectedFireAnimation = selectedFireAnimation
        fireAnimationLoader.preloadSelectedAnimation()
        viewModelScope.launch(dispatcherProvider.io()) {
            viewState.emit(currentViewState().copy(selectedFireAnimation = selectedFireAnimation))
        }
        pixel.fire(AppPixelName.FIRE_ANIMATION_NEW_SELECTED, mapOf(Pixel.PixelParameter.FIRE_ANIMATION to selectedFireAnimation.getPixelValue()))
    }

    private fun ClearWhatOption.pixelEvent(): Pixel.PixelName {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE
            ClearWhatOption.CLEAR_TABS_ONLY -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS
            ClearWhatOption.CLEAR_TABS_AND_DATA -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA
        }
    }

    private fun ClearWhenOption.pixelEvent(): Pixel.PixelName? {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY
            ClearWhenOption.APP_EXIT_OR_5_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_5_MINS
            ClearWhenOption.APP_EXIT_OR_15_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_15_MINS
            ClearWhenOption.APP_EXIT_OR_30_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_30_MINS
            ClearWhenOption.APP_EXIT_OR_60_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_60_MINS
            else -> null
        }
    }

    private fun isAutomaticallyClearingDataWhenSettingEnabled(clearWhatOption: ClearWhatOption?): Boolean {
        return clearWhatOption != null && clearWhatOption != ClearWhatOption.CLEAR_NONE
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
