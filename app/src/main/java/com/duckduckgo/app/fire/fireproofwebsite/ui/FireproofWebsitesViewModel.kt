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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ConfirmRemoveFireproofWebsite
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper.AutomaticFireproofSetting
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class FireproofWebsitesViewModel @Inject constructor(
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore
) : ViewModel() {

    data class ViewState(
        val automaticFireproofSetting: AutomaticFireproofSetting = AutomaticFireproofSetting.NEVER,
        val fireproofWebsitesEntities: List<FireproofWebsiteEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmRemoveFireproofWebsite(val entity: FireproofWebsiteEntity) : Command()
        class ConfirmRemoveAllFireproofWebsites(val removedWebsitesEntities: List<FireproofWebsiteEntity>) : Command()
        class ShowAutomaticFireproofSettingSelectionDialog(val automaticFireproofSetting: AutomaticFireproofSetting) : Command()
    }

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData()
    val viewState: LiveData<ViewState> = _viewState
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val fireproofWebsites: LiveData<List<FireproofWebsiteEntity>> = fireproofWebsiteRepository.getFireproofWebsites()
    private val fireproofWebsitesObserver = Observer<List<FireproofWebsiteEntity>> { onPreservedCookiesEntitiesChanged(it!!) }

    init {
        _viewState.value = ViewState(
            automaticFireproofSetting = settingsDataStore.automaticFireproofSetting
        )
        fireproofWebsites.observeForever(fireproofWebsitesObserver)
    }

    override fun onCleared() {
        super.onCleared()
        fireproofWebsites.removeObserver(fireproofWebsitesObserver)
    }

    private fun onPreservedCookiesEntitiesChanged(entities: List<FireproofWebsiteEntity>) {
        _viewState.value = _viewState.value?.copy(
            fireproofWebsitesEntities = entities
        )
    }

    fun onDeleteRequested(entity: FireproofWebsiteEntity) {
        command.value = ConfirmRemoveFireproofWebsite(entity)
    }

    fun onSnackBarUndoFireproof(entity: FireproofWebsiteEntity) {
        val domain = entity.domain
        viewModelScope.launch(dispatcherProvider.io()) {
            fireproofWebsiteRepository.fireproofWebsite(domain)
        }
    }

    fun remove(entity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            fireproofWebsiteRepository.removeFireproofWebsite(entity)
            pixel.fire(FIREPROOF_WEBSITE_DELETED)
        }
    }

    fun onAutomaticFireproofSettingChanged(newAutomaticFireproofSetting: AutomaticFireproofSetting) {
        Timber.i("User changed automatic fireproof setting, is now: ${newAutomaticFireproofSetting.name}")

        viewModelScope.launch(dispatcherProvider.io()) {
            if (newAutomaticFireproofSetting != AutomaticFireproofSetting.NEVER) {
                userEventsStore.registerUserEvent(UserEventKey.USER_ENABLED_FIREPROOF_LOGIN)
            }
        }

        settingsDataStore.automaticFireproofSetting = newAutomaticFireproofSetting
        val pixelName =
            when (newAutomaticFireproofSetting) {
                AutomaticFireproofSetting.ASK_EVERY_TIME -> FIREPROOF_SETTING_SELECTION_ASK_EVERYTIME
                AutomaticFireproofSetting.ALWAYS -> FIREPROOF_SETTING_SELECTION_ALWAYS
                AutomaticFireproofSetting.NEVER -> FIREPROOF_SETTING_SELECTION_NEVER
            }
        _viewState.value = _viewState.value?.copy(automaticFireproofSetting = newAutomaticFireproofSetting)

        pixel.fire(pixelName)
    }

    fun removeAllRequested() {
        val removedWebsites = fireproofWebsites.value ?: emptyList()
        command.value = Command.ConfirmRemoveAllFireproofWebsites(removedWebsites)
    }

    fun removeAllWebsites() {
        viewModelScope.launch(dispatcherProvider.io()) {

            fireproofWebsiteRepository.removeAllFireproofWebsites()
            pixel.fire(FIREPROOF_WEBSITE_ALL_DELETED)
        }
    }

    fun onSnackBarUndoRemoveAllWebsites(removedWebsitesEntities: List<FireproofWebsiteEntity>) {
        viewModelScope.launch(dispatcherProvider.io()) {
            removedWebsitesEntities.forEach { fireproofWebsiteEntity ->
                onSnackBarUndoFireproof(fireproofWebsiteEntity)
            }
        }
    }
}
