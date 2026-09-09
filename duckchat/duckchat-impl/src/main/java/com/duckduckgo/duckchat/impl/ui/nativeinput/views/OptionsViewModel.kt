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
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModel
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModelProvider
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class OptionsViewModel @Inject constructor(
    nativeInputStateProvider: NativeInputStateProvider,
    private val duckChatPixels: DuckChatPixels,
    modelManager: DuckAiModelManager,
    effectiveModelProvider: EffectiveModelProvider,
) : ViewModel() {

    val selectedTool: StateFlow<Tool?> = nativeInputStateProvider.state
        .map { state -> state.selectedTool?.let { Tool.from(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val surface: StateFlow<DuckChatPixelSurface> = nativeInputStateProvider.state
        .map { DuckChatPixelSurface.from(it.inputContext) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DuckChatPixelSurface.ADDRESS_BAR)

    /**
     * Tools the active tab's model supports. Every tool while that model is unknown, which is what the
     * pre-plugin code fell back to, and what keeps a switch from briefly hiding a supported tool.
     */
    val visibleTools: StateFlow<Set<Tool>> = combine(
        nativeInputStateProvider.state,
        modelManager.modelState,
        effectiveModelProvider.effectiveModel,
    ) { state, modelState, effective ->
        val model = effective.modelFor(state.chatId)?.let { id -> modelState.models.firstOrNull { it.id == id } }
        model?.let { Tool.entries.filterTo(mutableSetOf()) { tool -> it.supportsTool(tool) } } ?: Tool.entries.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Tool.entries.toSet())

    val shouldShowPickers: Boolean get() = selectedTool.value != Tool.IMAGE_GENERATION

    /**
     * Emits when the tab's own model does not support its selected tool. Deliberately not a pixel: the
     * user did not deselect it, the model change did.
     */
    val toolSelectionCleared: Flow<Unit> = combine(
        nativeInputStateProvider.state,
        modelManager.modelState,
        effectiveModelProvider.effectiveModel,
    ) { state, modelState, effective ->
        val selected = state.selectedTool?.let { Tool.from(it) }
        val modelId = effective.modelFor(state.chatId)
        // Both read from the same state emission, so the selection and the chat it belongs to always agree.
        selected != null && modelId != null &&
            modelState.models.firstOrNull { it.id == modelId }?.supportsTool(selected) == false
    }.filter { it }.map { }

    /** The model id only when it was resolved for [chatId]; a model resolved for another chat is stale here. */
    private fun EffectiveModel.modelFor(chatId: String?): String? =
        (this as? EffectiveModel.Resolved)?.takeIf { it.chatId == chatId }?.modelId

    fun onToolSelectedByUser(tool: Tool) {
        when (tool) {
            Tool.IMAGE_GENERATION -> duckChatPixels.fireImageGenerationSelected(surface.value)
            Tool.WEB_SEARCH -> duckChatPixels.fireWebSearchSelected(surface.value)
        }
    }

    fun onToolDeselectedByUser(tool: Tool) {
        when (tool) {
            Tool.IMAGE_GENERATION -> duckChatPixels.fireImageGenerationDeselected(surface.value)
            Tool.WEB_SEARCH -> duckChatPixels.fireWebSearchDeselected(surface.value)
        }
    }

    fun onCustomizeResponsesClicked() {
        duckChatPixels.fireCustomizeResponsesSelected(surface.value)
    }
}
