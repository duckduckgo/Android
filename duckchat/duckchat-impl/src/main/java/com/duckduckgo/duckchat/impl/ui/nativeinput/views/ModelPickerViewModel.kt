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
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

data class ModelSection(@StringRes val headerRes: Int?, val models: List<AIChatModel>)

/** Emitted when the user picks a model during the FE recovery model-change flow. */
sealed class PickerModelChange {
    data class ChangeModel(val modelId: String) : PickerModelChange()
}

@ContributesViewModel(ViewScope::class)
class ModelPickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val duckChatPixels: DuckChatPixels,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val duckAiChatStore: DuckAiChatStore,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    private val currentChat = MutableStateFlow<DuckAiChat?>(null)

    private var modelChangeMode: Boolean = false

    // The model picked during the current recovery flow. Display-only: it drives the chip and the
    // picker's selected tick immediately, without waiting for the FE to sync the chat's model back
    // to native storage. Cleared when the recovery window ends.
    private val recoverySelectedModelId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            // collectLatest: cancel in-flight lookup on chatId flip to avoid stale currentChat.
            nativeInputStateProvider.state
                .map { it.chatId }
                .distinctUntilChanged()
                .collectLatest { chatId ->
                    currentChat.value = if (chatId == null) null else duckAiChatStore.getChatById(chatId)
                }
        }
        viewModelScope.launch {
            nativeInputStateProvider.state.collect { state ->
                modelChangeMode = state.modelChangeMode
                if (!state.modelChangeMode) recoverySelectedModelId.value = null
            }
        }
    }

    /**
     * The model the chip displays and whose capabilities the options should reflect: a just-picked
     * recovery model, else the active chat's model (when in the list, e.g. not lost access), else
     * the global selection. Single source of truth for [chipLabel] and [getSelectedModel].
     */
    val effectiveModelId: StateFlow<String?> = combine(
        modelManager.modelState,
        nativeInputStateProvider.state,
        currentChat,
        recoverySelectedModelId,
    ) { modelState, nativeState, chat, recoveryId ->
        val modelIds = modelState.models.mapTo(HashSet()) { it.id }
        // A just-picked recovery model wins (display, before the chat's model syncs back).
        recoveryId?.takeIf { it in modelIds }?.let { return@combine it }
        val activeChat = chat?.takeIf { it.chatId == nativeState.chatId }
        activeChat?.model?.takeIf { it in modelIds } ?: modelState.selectedModelId
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = modelManager.modelState.value.selectedModelId,
    )

    /**
     * Chat-aware chip label derived from [effectiveModelId]: that model's short name, falling back
     * to the global selection's short name when it isn't in the list.
     */
    val chipLabel: StateFlow<String?> = combine(modelManager.modelState, effectiveModelId) { modelState, id ->
        modelState.models.firstOrNull { it.id == id }?.shortName ?: modelState.selectedModelShortName
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = modelManager.modelState.value.selectedModelShortName,
    )

    /** True once a model was picked during the current recovery window (set synchronously in onModelTapped). */
    fun hasPendingRecoverySelection(): Boolean = recoverySelectedModelId.value != null

    /**
     * Model id the picker should mark with the selected tick: prioritizes the recovery model if any,
     * then the active chat's model during recovery, then
     * the global selection (normal new-chat behaviour).
     */
    fun selectedModelIdForMenu(): String? {
        recoverySelectedModelId.value?.let { return it }
        val chat = currentChat.value
        if (modelChangeMode && chat != null) return chat.model
        return modelManager.modelState.value.selectedModelId
    }

    var menuShowing = false

    fun getSelectedModelId(): String? = modelManager.getSelectedModelId()

    fun getSelectedModel(): AIChatModel? = state.value.models.firstOrNull { it.id == effectiveModelId.value }

    fun isImageGenerationSupported(): Boolean = getSelectedModel()?.supportsTool(Tool.IMAGE_GENERATION) ?: true

    fun isWebSearchSupported(): Boolean = getSelectedModel()?.supportsTool(Tool.WEB_SEARCH) ?: true

    fun fetchModels() {
        viewModelScope.launch {
            modelManager.fetchModels()
        }
    }

    private val command = Channel<UpsellCommand>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<UpsellCommand> = command.receiveAsFlow()

    private val modelChangeChannel = Channel<PickerModelChange>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val modelChanges: Flow<PickerModelChange> = modelChangeChannel.receiveAsFlow()

    fun onModelTapped(model: AIChatModel, surface: PickerSurface) {
        if (model.isAccessible) {
            if (modelChangeMode) {
                // FE recovery flow: report the chosen model to the FE instead of changing the
                // global default. FE owns the chat's model; native storage syncs back. Show the
                // pick immediately on the chip + tick rather than waiting for that sync.
                // Fire the same selection pixel as the normal flow (skip a re-tap of the same pick).
                if (model.id != recoverySelectedModelId.value) {
                    duckChatPixels.fireModelSelected(model.id)
                }
                recoverySelectedModelId.value = model.id
                modelChangeChannel.trySend(PickerModelChange.ChangeModel(model.id))
            } else {
                if (model.id != modelManager.getSelectedModelId()) {
                    duckChatPixels.fireModelSelected(model.id)
                }
                viewModelScope.launch { modelManager.selectModel(model) }
            }
            return
        }
        val userTier = modelManager.modelState.value.userTier
        val requiredTier = model.requiredTier ?: run {
            logcat { "Duck.ai picker: tapped model has no public required tier (id=${model.id}, accessTier=${model.accessTier}), ignoring." }
            return
        }
        routeUpsell(userTier, requiredTier, surface.origin)?.let { upsell ->
            duckChatPixels.fireSubscriptionUpsellTriggered(
                source = "model_picker",
                currentTier = userTier.toParam(),
                requiredTier = requiredTier.toParam(),
                flowType = upsell.toFlowTypeParam(),
            )
            command.trySend(upsell)
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

    private fun List<AIChatModel>.toSectionOrNull(@StringRes headerRes: Int?): ModelSection? =
        takeIf { it.isNotEmpty() }?.let { ModelSection(headerRes, it) }
}
