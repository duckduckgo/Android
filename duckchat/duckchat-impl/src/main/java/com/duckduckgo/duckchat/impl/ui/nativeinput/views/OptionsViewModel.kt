/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.models.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class OptionsViewModel @Inject constructor(
    nativeInputStateProvider: NativeInputStateProvider,
) : ViewModel() {

    val selectedTool: StateFlow<Tool?> = nativeInputStateProvider.state
        .map { state -> state.selectedTool?.let { Tool.from(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _visibleTools = MutableStateFlow(Tool.entries.toSet())
    val visibleTools: StateFlow<Set<Tool>> = _visibleTools.asStateFlow()

    val shouldShowPickers: Boolean get() = selectedTool.value != Tool.IMAGE_GENERATION

    /**
     * Update the set of tools the menu should display. Returns true if the currently selected tool
     * is no longer in the visible set (caller is responsible for pushing the cleared selection
     * through [com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost.toolSelected]).
     */
    fun updateVisibleTools(tools: Set<Tool>): Boolean {
        _visibleTools.value = tools
        val current = selectedTool.value
        return current != null && current !in tools
    }
}
