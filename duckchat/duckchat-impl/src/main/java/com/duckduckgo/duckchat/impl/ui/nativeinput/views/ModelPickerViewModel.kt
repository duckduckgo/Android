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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.models.UserTier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

data class ModelSection(@StringRes val headerRes: Int?, val models: List<AIChatModel>)

@ContributesViewModel(ViewScope::class)
class ModelPickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    var menuShowing = false

    fun getSelectedModelId(): String? = modelManager.getSelectedModelId()

    fun getSelectedModel(): AIChatModel? = state.value.run { models.find { it.id == selectedModelId } }

    fun isImageGenerationSupported(): Boolean = getSelectedModel()?.supportsTool(Tool.IMAGE_GENERATION) ?: true

    fun isWebSearchSupported(): Boolean = getSelectedModel()?.supportsTool(Tool.WEB_SEARCH) ?: true

    fun fetchModels() {
        viewModelScope.launch {
            modelManager.fetchModels()
        }
    }

    private val command = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<Command> = command.receiveAsFlow()

    fun onModelTapped(model: AIChatModel, surface: PickerSurface) {
        if (model.isAccessible) {
            viewModelScope.launch { modelManager.selectModel(model) }
            return
        }
        val userTier = modelManager.modelState.value.userTier
        val requiredTier = model.requiredTier ?: run {
            logcat { "Duck.ai picker: tapped model has no public required tier (id=${model.id}, accessTier=${model.accessTier}), ignoring." }
            return
        }
        when {
            requiredTier == UserTier.FREE -> {
                logcat { "Duck.ai picker: inaccessible model with FREE required tier (id=${model.id}), no upsell route." }
            }
            userTier == UserTier.FREE -> command.trySend(Command.LaunchPurchase(surface.origin))
            userTier == UserTier.PLUS && requiredTier == UserTier.PRO -> command.trySend(Command.LaunchUpgrade(surface.origin))
            else -> {
                logcat { "Duck.ai picker: no native subscription flow for tap (userTier=$userTier, requiredTier=$requiredTier, id=${model.id})." }
            }
        }
    }

    fun buildSections(state: ModelState): List<ModelSection> {
        val byTier = state.models.groupBy { it.requiredTier }
        // Models with a null requiredTier (non-public access tiers only, e.g. "internal") have no
        // section to land in and are intentionally hidden from the picker.
        return listOfNotNull(
            byTier[UserTier.FREE].orEmpty().toSectionOrNull(headerRes = null),
            byTier[UserTier.PLUS].orEmpty().toSectionOrNull(R.string.duckAiModelPickerPlusModels),
            byTier[UserTier.PRO].orEmpty().toSectionOrNull(R.string.duckAiModelPickerProModels),
        )
    }

    @DrawableRes
    fun getIconResForModel(model: AIChatModel): Int? = when (model.provider) {
        ModelProvider.OPENAI -> R.drawable.ic_ai_model_openai_16
        ModelProvider.ANTHROPIC -> R.drawable.ic_ai_model_claude_16
        ModelProvider.MISTRAL -> R.drawable.ic_ai_model_mistral_16
        ModelProvider.META -> R.drawable.ic_ai_model_llama_16
        ModelProvider.OSS -> R.drawable.ic_ai_model_oss_16
        ModelProvider.UNKNOWN -> null
    }

    sealed class Command {
        data class LaunchPurchase(val origin: String) : Command()
        data class LaunchUpgrade(val origin: String) : Command()
    }

    private fun List<AIChatModel>.toSectionOrNull(@StringRes headerRes: Int?): ModelSection? =
        takeIf { it.isNotEmpty() }?.let { ModelSection(headerRes, it) }
}

enum class PickerSurface(val origin: String) {
    ADDRESS_BAR("funnel_nativeinput_androidapp__modelpicker"),
    DUCK_AI_TAB("funnel_duckai_androidapp__modelpicker"),
}
