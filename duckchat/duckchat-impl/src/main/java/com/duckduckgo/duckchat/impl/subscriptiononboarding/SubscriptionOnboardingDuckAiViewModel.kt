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

package com.duckduckgo.duckchat.impl.subscriptiononboarding

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint.ONBOARDING
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.subscriptiononboarding.SubscriptionOnboardingDuckAiStepPlugin.Companion.DUCK_AI_STEP_ID
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.SKIPPED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingDuckAiViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val duckChat: DuckChat,
    private val duckAiModelManager: DuckAiModelManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    init {
        if (!duckChat.isEnabled()) {
            viewState.update { it.copy(aiEnabled = false) }
        } else {
            viewState.update { it.copy(aiEnabled = true) }
            viewModelScope.launch(dispatcherProvider.io()) { duckAiModelManager.fetchModels() }
            duckAiModelManager.modelState
                .onEach { modelState ->
                    val models = modelState.models
                        .filter { it.isAvailableTo(modelState.userTier) }
                        .map { it.toItem() }
                        .sortedByDescending { it.tier.rank }
                    val selectedId = viewState.value.selectedModelId?.takeIf { id -> models.any { it.id == id } }
                        ?: models.firstOrNull()?.id
                    viewState.update { it.copy(models = models, selectedModelId = selectedId) }
                }
                .flowOn(dispatcherProvider.io())
                .launchIn(viewModelScope)
        }
    }

    fun onModelSelected(modelId: String) {
        viewState.update { it.copy(selectedModelId = modelId) }
    }

    fun onStartClicked() {
        val selectedId = viewState.value.selectedModelId ?: return
        viewModelScope.launch {
            duckAiModelManager.modelState.value.models.firstOrNull { it.id == selectedId }?.let {
                duckAiModelManager.selectModel(it)
            }
            duckChat.openDuckChat(ONBOARDING)
            controller.exitOnboarding()
        }
    }

    fun onNotNowClicked() {
        controller.onStepFinished(DUCK_AI_STEP_ID, SKIPPED)
    }

    fun onPlaceholderPrimaryClicked() {
        controller.onStepFinished(DUCK_AI_STEP_ID, COMPLETED)
    }

    private fun AIChatModel.toItem(): ModelItem = ModelItem(
        id = id,
        displayName = displayName,
        iconRes = iconForProvider(provider),
        tier = requiredTier ?: UserTier.FREE,
    )

    data class ViewState(
        // null until the AI-features availability is known, so the UI does not flash the wrong screen.
        val aiEnabled: Boolean? = null,
        val models: List<ModelItem> = emptyList(),
        val selectedModelId: String? = null,
    )

    data class ModelItem(
        val id: String,
        val displayName: String,
        @DrawableRes val iconRes: Int,
        val tier: UserTier,
    )
}

private fun AIChatModel.isAvailableTo(userTier: UserTier): Boolean {
    val required = requiredTier ?: return false
    return required.rank <= userTier.rank
}

private val UserTier.rank: Int
    get() = when (this) {
        UserTier.FREE -> 0
        UserTier.PLUS -> 1
        UserTier.PRO -> 2
    }

@DrawableRes
private fun iconForProvider(provider: ModelProvider): Int = when (provider) {
    ModelProvider.OPENAI -> R.drawable.ic_ai_model_openai_16
    ModelProvider.ANTHROPIC -> R.drawable.ic_ai_model_claude_16
    ModelProvider.MISTRAL -> R.drawable.ic_ai_model_mistral_16
    ModelProvider.META -> R.drawable.ic_ai_model_llama_16
    ModelProvider.OSS, ModelProvider.UNKNOWN -> R.drawable.ic_ai_model_oss_16
}
