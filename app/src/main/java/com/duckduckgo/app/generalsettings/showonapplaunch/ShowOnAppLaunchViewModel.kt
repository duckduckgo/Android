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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ShowOnAppLaunchViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val urlConverter: UrlConverter,
) : ViewModel() {

    data class ViewState(
        val selectedOption: ShowOnAppLaunchOption,
        val specificPageUrl: String,
    )

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow().filterNotNull()

    init {
        observeShowOnAppLaunchOptionChanges()
    }

    private fun observeShowOnAppLaunchOptionChanges() {
        combine(
            showOnAppLaunchOptionDataStore.optionFlow,
            showOnAppLaunchOptionDataStore.specificPageUrlFlow,
        ) { option, specificPageUrl ->
            _viewState.value = ViewState(option, specificPageUrl)
        }.flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onShowOnAppLaunchOptionChanged(option: ShowOnAppLaunchOption) {
        viewModelScope.launch(dispatcherProvider.io()) {
            showOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(option)
        }
    }

    fun setSpecificPageUrl(url: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val convertedUrl = urlConverter.convertUrl(url)
            showOnAppLaunchOptionDataStore.setSpecificPageUrl(convertedUrl)
        }
    }
}
