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
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchViewModel.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchViewModel.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchViewModel.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class ShowOnAppLaunchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    sealed class ShowOnAppLaunchOption {

        data object LastOpenedTab : ShowOnAppLaunchOption()
        data object NewTabPage : ShowOnAppLaunchOption()
        data class SpecificPage(val url: String) : ShowOnAppLaunchOption()
    }

    data class ViewState(
        val selectedOption: ShowOnAppLaunchOption,
    )

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            // TODO get selected option from prefs

            _viewState.value = ViewState(
                selectedOption = LastOpenedTab,
            )
        }
    }

    fun onShowOnAppLaunchOptionChanged(option: ShowOnAppLaunchOption) {
        Timber.i("User changed show on app launch option to $option")
        when (option) {
            LastOpenedTab -> _viewState.update { it?.copy(selectedOption = option) }
            NewTabPage -> _viewState.update { it?.copy(selectedOption = option) }
            is SpecificPage -> {
                // TODO get the last set page if we have one and populate the url
                _viewState.update { it?.copy(selectedOption = SpecificPage("duckduckgo.com")) }
            }
        }
    }
}
