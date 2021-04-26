/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider

class GlobalPrivacyControlViewModel(
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    data class ViewState(
        val globalPrivacyControlEnabled: Boolean = false
    )

    sealed class Command {
        class OpenLearnMore(val url: String = LEARN_MORE_URL) : Command()
    }

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData()
    val viewState: LiveData<ViewState> = _viewState
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        _viewState.value = ViewState(
            globalPrivacyControlEnabled = settingsDataStore.globalPrivacyControlEnabled
        )
        pixel.fire(SETTINGS_DO_NOT_SELL_SHOWN)
    }

    fun onUserToggleGlobalPrivacyControl(enabled: Boolean) {
        val pixelName = if (enabled) SETTINGS_DO_NOT_SELL_ON else SETTINGS_DO_NOT_SELL_OFF
        pixel.fire(pixelName)
        settingsDataStore.globalPrivacyControlEnabled = enabled
        _viewState.value = _viewState.value?.copy(globalPrivacyControlEnabled = enabled)
    }

    fun onLearnMoreSelected() {
        command.value = Command.OpenLearnMore()
    }

    companion object {
        const val LEARN_MORE_URL = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/gpc/"
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class GlobalPrivacyControlViewModelFactory @Inject constructor(
    private val pixel: Provider<Pixel>,
    private val settingsDataStore: Provider<SettingsDataStore>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(GlobalPrivacyControlViewModel::class.java) -> (GlobalPrivacyControlViewModel(pixel.get(), settingsDataStore.get()) as T)
                else -> null
            }
        }
    }
}
