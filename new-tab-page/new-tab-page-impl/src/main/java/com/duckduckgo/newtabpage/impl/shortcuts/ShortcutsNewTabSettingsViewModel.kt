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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class ShortcutsNewTabSettingsViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val dataStore: NewTabShortcutDataStore,
    private val pixels: NewTabPixels,
) : ViewModel(), DefaultLifecycleObserver {

    private val _viewState = MutableStateFlow(ViewState(true))
    val viewState = _viewState.asStateFlow()

    data class ViewState(val enabled: Boolean)

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        viewModelScope.launch(dispatchers.io()) {
            val isEnabled = dataStore.isEnabled()
            withContext(dispatchers.main()) {
                _viewState.update { ViewState(isEnabled) }
            }
        }
    }

    fun onSettingEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            dataStore.setIsEnabled(enabled)
            withContext(dispatchers.main()) {
                _viewState.update { ViewState(enabled) }
            }
            pixels.fireShortcutSectionToggled(enabled)
        }
    }
}
