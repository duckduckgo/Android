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

package com.duckduckgo.duckchat.impl.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSection(@StringRes val headerRes: Int?, val models: List<AIChatModel>)

@ContributesViewModel(ViewScope::class)
class ModelPickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    var menuShowing = false

    fun getSelectedModelId(): String? = modelManager.getSelectedModelId()

    fun fetchModels() {
        viewModelScope.launch {
            modelManager.fetchModels()
        }
    }

    fun selectModel(model: AIChatModel) {
        viewModelScope.launch {
            modelManager.selectModel(model)
        }
    }

    fun buildSections(state: ModelState): List<ModelSection> =
        if (state.userTier != UserTier.FREE) getSubscriberModels(state.models) else getFreeModels(state.models)

    private fun getSubscriberModels(models: List<AIChatModel>): List<ModelSection> {
        val advanced = models.filter { !it.accessTier.contains(FREE_TIER) }
        val basic = models.filter { it.accessTier.contains(FREE_TIER) }
        return listOfNotNull(
            advanced.toSectionOrNull(R.string.duckAiModelPickerAdvancedModels),
            basic.toSectionOrNull(R.string.duckAiModelPickerBasicModels),
        )
    }

    private fun getFreeModels(models: List<AIChatModel>): List<ModelSection> {
        val accessible = models.filter { it.isAccessible }
        val premium = models.filter { !it.isAccessible }
        return listOfNotNull(
            accessible.toSectionOrNull(headerRes = null),
            premium.toSectionOrNull(R.string.duckAiModelPickerPremiumModels),
        )
    }

    private fun List<AIChatModel>.toSectionOrNull(@StringRes headerRes: Int?): ModelSection? =
        takeIf { it.isNotEmpty() }?.let { ModelSection(headerRes, it) }

    companion object {
        private const val FREE_TIER = "free"
    }
}
