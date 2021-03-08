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
import androidx.lifecycle.Observer
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ConfirmDeleteFireproofWebsite
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class FireproofWebsitesViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideFireproofWebsitesViewModelFactory(
        fireproofWebsiteRepository: FireproofWebsiteRepository,
        dispatcherProvider: DispatcherProvider,
        pixel: Pixel,
        settingsDataStore: SettingsDataStore,
        userEventsStore: UserEventsStore
    ): ViewModelFactoryPlugin {
        return FireproofWebsitesViewModelFactory(fireproofWebsiteRepository, dispatcherProvider, pixel, settingsDataStore, userEventsStore)
    }
}

private class FireproofWebsitesViewModelFactory(
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(FireproofWebsitesViewModel::class.java) -> (FireproofWebsitesViewModel(fireproofWebsiteRepository, dispatcherProvider, pixel, settingsDataStore, userEventsStore) as T)
                else -> null
            }
        }
    }
}

class FireproofWebsitesViewModel(
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore
) : ViewModel() {

    data class ViewState(
        val loginDetectionEnabled: Boolean = false,
        val fireproofWebsitesEntities: List<FireproofWebsiteEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmDeleteFireproofWebsite(val entity: FireproofWebsiteEntity) : Command()
    }

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData()
    val viewState: LiveData<ViewState> = _viewState
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val fireproofWebsites: LiveData<List<FireproofWebsiteEntity>> = fireproofWebsiteRepository.getFireproofWebsites()
    private val fireproofWebsitesObserver = Observer<List<FireproofWebsiteEntity>> { onPreservedCookiesEntitiesChanged(it!!) }

    init {
        _viewState.value = ViewState(
            loginDetectionEnabled = settingsDataStore.appLoginDetection
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
        command.value = ConfirmDeleteFireproofWebsite(entity)
    }

    fun onSnackBarUndoFireproof(entity: FireproofWebsiteEntity) {
        val domain = entity.domain
        viewModelScope.launch(dispatcherProvider.io()) {
            fireproofWebsiteRepository.fireproofWebsite(domain)
        }
    }

    fun delete(entity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            fireproofWebsiteRepository.removeFireproofWebsite(entity)
            pixel.fire(FIREPROOF_WEBSITE_DELETED)
        }
    }

    fun onUserToggleLoginDetection(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val pixelName = if (enabled) FIREPROOF_LOGIN_TOGGLE_ENABLED else FIREPROOF_LOGIN_TOGGLE_DISABLED
            pixel.fire(pixelName)

            if (enabled) {
                userEventsStore.registerUserEvent(UserEventKey.USER_ENABLED_FIREPROOF_LOGIN)
            }
        }
        settingsDataStore.appLoginDetection = enabled
        _viewState.value = _viewState.value?.copy(loginDetectionEnabled = enabled)
    }
}
