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

package com.duckduckgo.newtabpage.impl.settings

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ActivityScope::class)
class NewTabSettingsViewModel @Inject constructor(
    private val newTabPageSectionSettingsProvider: NewTabPageSectionSettingsProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> =
        _viewState.onStart {
            configureViews()
        }.flowOn(dispatcherProvider.io())

    data class ViewState(val sections: List<NewTabPageSectionSettings> = emptyList())

    private fun configureViews() {
        viewModelScope.launch(dispatcherProvider.io()) {
            newTabPageSectionSettingsProvider.provideSections().collect { sections ->
                withContext(dispatcherProvider.main()) {
                    _viewState.update { ViewState(sections) }
                }
            }
        }
    }
}
