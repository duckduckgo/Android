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

package com.duckduckgo.app.icon.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.icon.api.IconModifier
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider

class ChangeIconViewModel
@Inject
constructor(
    private val settingsDataStore: SettingsDataStore,
    private val appIconModifier: IconModifier,
    private val pixel: Pixel
) : ViewModel() {

    data class IconViewData(val appIcon: AppIcon, val selected: Boolean) {
        companion object {
            fun from(appIcon: AppIcon, selectedAppIcon: AppIcon): IconViewData {
                return if (appIcon.componentName == selectedAppIcon.componentName) {
                    IconViewData(appIcon, true)
                } else {
                    IconViewData(appIcon, false)
                }
            }
        }
    }

    data class ViewState(val appIcons: List<IconViewData>)

    sealed class Command {
        object IconChanged : Command()
        data class ShowConfirmationDialog(val viewData: IconViewData) : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun start() {
        pixel.fire(AppPixelName.CHANGE_APP_ICON_OPENED)
        val selectedIcon = settingsDataStore.appIcon
        viewState.value = ViewState(AppIcon.values().map { IconViewData.from(it, selectedIcon) })
    }

    fun onIconSelected(viewData: IconViewData) {
        command.value = Command.ShowConfirmationDialog(viewData)
    }

    fun onIconConfirmed(viewData: IconViewData) {
        val previousIcon = settingsDataStore.appIcon
        settingsDataStore.appIcon = viewData.appIcon
        settingsDataStore.appIconChanged = true
        appIconModifier.changeIcon(previousIcon, viewData.appIcon)
        command.value = Command.IconChanged
    }
}

@ContributesMultibinding(AppScope::class)
class ChangeIconViewModelFactory
@Inject
constructor(
    private val settingsDataStore: Provider<SettingsDataStore>,
    private val appIconModifier: Provider<IconModifier>,
    private val pixel: Provider<Pixel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(ChangeIconViewModel::class.java) ->
                    (ChangeIconViewModel(
                        settingsDataStore.get(), appIconModifier.get(), pixel.get()) as
                        T)
                else -> null
            }
        }
    }
}
