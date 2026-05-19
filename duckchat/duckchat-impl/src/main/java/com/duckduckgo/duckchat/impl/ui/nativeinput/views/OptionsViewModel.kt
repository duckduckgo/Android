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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.models.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class OptionsViewModel @Inject constructor() : ViewModel() {

    private val _selectedTool = MutableStateFlow<Tool?>(null)
    val selectedTool: StateFlow<Tool?> = _selectedTool.asStateFlow()

    private val _visibleTools = MutableStateFlow(Tool.entries.toSet())
    val visibleTools: StateFlow<Set<Tool>> = _visibleTools.asStateFlow()

    val shouldShowPickers: Boolean get() = _selectedTool.value != Tool.IMAGE_GENERATION

    fun toggleTool(tool: Tool) {
        _selectedTool.value = if (tool == _selectedTool.value) null else tool
    }

    fun clearTool() {
        _selectedTool.value = null
    }

    fun updateVisibleTools(tools: Set<Tool>): Boolean {
        _visibleTools.value = tools
        return if (_selectedTool.value != null && _selectedTool.value !in tools) {
            _selectedTool.value = null
            true
        } else {
            false
        }
    }
}
