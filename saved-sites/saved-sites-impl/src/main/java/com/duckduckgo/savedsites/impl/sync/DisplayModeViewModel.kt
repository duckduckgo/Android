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

package com.duckduckgo.savedsites.impl.sync

import androidx.lifecycle.*
import com.duckduckgo.app.global.*
import com.duckduckgo.savedsites.impl.SavedSitesSettingsRepository
import com.duckduckgo.savedsites.store.FavoritesViewMode
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DisplayModeViewModel(
    private val savedSitesSettingsRepository: SavedSitesSettingsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val shareFavoritesEnabled: Boolean = false,
    )

    fun viewState(): Flow<ViewState> = savedSitesSettingsRepository.viewModeFlow().map { viewMode ->
        ViewState(
            shareFavoritesEnabled = viewMode == FavoritesViewMode.UNIFIED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    fun onDisplayModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val viewMode = if (checked) FavoritesViewMode.UNIFIED else FavoritesViewMode.NATIVE
            savedSitesSettingsRepository.favoritesDisplayMode = viewMode
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val savedSitesSettingsRepository: SavedSitesSettingsRepository,
        private val dispatcherProvider: DispatcherProvider,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(DisplayModeViewModel::class.java) -> DisplayModeViewModel(
                        savedSitesSettingsRepository,
                        dispatcherProvider,
                    )
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
