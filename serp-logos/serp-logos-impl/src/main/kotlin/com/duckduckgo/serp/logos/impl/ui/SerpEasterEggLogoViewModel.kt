/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.serp.logos.api.SerpEasterEggLogosToggles
import com.duckduckgo.serp.logos.impl.store.FavouriteSerpLogoDataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SerpEasterEggLogoViewModel @Inject constructor(
    private val favouriteSerpLogoDataStore: FavouriteSerpLogoDataStore,
    private val serpEasterEggLogosToggles: SerpEasterEggLogosToggles,
) : ViewModel() {

    private var setLogoConflatedJob = ConflatedJob()

    data class ViewState(
        val logoUrl: String = "",
        val isFavourite: Boolean = false,
        val isSetFavouriteEnabled: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    sealed interface Command {
        data object CloseScreen : Command
    }

    private val _command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands: Flow<Command> = _command.receiveAsFlow()

    private var currentLogoUrl: String = ""

    fun setLogoUrl(logoUrl: String) {
        currentLogoUrl = logoUrl
        setLogoConflatedJob += viewModelScope.launch {
            combine(
                serpEasterEggLogosToggles.setFavourite().enabled(),
                favouriteSerpLogoDataStore.favouriteSerpEasterEggLogoUrlFlow,
            ) { isEnabled, storedFavourite ->
                ViewState(
                    logoUrl = logoUrl,
                    isFavourite = storedFavourite == logoUrl,
                    isSetFavouriteEnabled = isEnabled,
                )
            }.collect { viewState ->
                _viewState.value = viewState
            }
        }
    }

    fun onBackgroundClicked() {
        _command.trySend(Command.CloseScreen)
    }

    fun onFavouriteButtonClicked() {
        viewModelScope.launch {
            if (_viewState.value.isFavourite) {
                favouriteSerpLogoDataStore.clearFavouriteLogo()
            } else {
                favouriteSerpLogoDataStore.setFavouriteLogo(currentLogoUrl)
            }
            _command.send(Command.CloseScreen)
        }
    }
}
