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
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelLabel
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModel
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModelProvider
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
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

data class ModelSection(
    @StringRes val headerRes: Int?,
    val models: List<AIChatModel>,
    /** Rows in a gated section open an upsell instead of selecting, so they render the follow-up ellipsis. */
    val gated: Boolean = false,
)

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
    private val effectiveModelProvider: EffectiveModelProvider,
    private val duckChatFeature: DuckChatFeature,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    private val currentChat = MutableStateFlow<DuckAiChat?>(null)

    private var modelChangeMode: Boolean = false

    // The published chatId, which is the key for the recovery pick. Read from state rather than
    // currentChat, which can still be resolving when the user picks.
    private var publishedChatId: String? = null

    // The pixel surface for the active tab, tracked from the published input context.
    // Named distinctly from the [PickerSurface] param on [onModelTapped] to avoid shadowing.
    private var pixelSurface: DuckChatPixelSurface = DuckChatPixelSurface.ADDRESS_BAR

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
                publishedChatId = state.chatId
                pixelSurface = DuckChatPixelSurface.from(state.inputContext)
                if (!state.modelChangeMode) {
                    recoverySelectedModelId.value = null
                    effectiveModelProvider.clearRecoveryModelPick(state.chatId)
                }
            }
        }
    }

    /**
     * The model the chip displays. An answer resolved for a different chat is ignored: state and the
     * provider arrive on separate flows, so a tab switch can pair this tab's state with the previous
     * tab's model, and the chip would name a model this chat is not using.
     */
    val effectiveModelId: StateFlow<String?> = combine(
        nativeInputStateProvider.state,
        effectiveModelProvider.effectiveModel,
        modelManager.modelState,
    ) { state, effective, modelState ->
        (effective as? EffectiveModel.Resolved)?.takeIf { it.chatId == state.chatId }?.modelId
            ?: modelState.selectedModelId
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

    fun fetchModels() {
        viewModelScope.launch {
            modelManager.fetchModels()
        }
    }

    private val command = Channel<UpsellCommand>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<UpsellCommand> = command.receiveAsFlow()

    private val modelChangeChannel = Channel<PickerModelChange>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val modelChanges: Flow<PickerModelChange> = modelChangeChannel.receiveAsFlow()

    /** Subscription-funnel impression: the model picker was shown to the user. */
    fun onPickerShown(surface: PickerSurface) {
        duckChatPixels.fireModelPickerShown(effectiveOrigin(surface))
    }

    /**
     * The subscription-funnel origin for [surface]. [SWITCH_MODEL_ORIGIN] is used when
     * the picker is open in the FE model-recovery ("switch model") flow.
     */
    private fun effectiveOrigin(surface: PickerSurface): String =
        if (modelChangeMode) SWITCH_MODEL_ORIGIN else surface.origin

    fun onModelTapped(model: AIChatModel, surface: PickerSurface) {
        if (model.isAccessible) {
            if (modelChangeMode) {
                // FE recovery flow: report the chosen model to the FE instead of changing the
                // global default. FE owns the chat's model; native storage syncs back. Show the
                // pick immediately on the chip + tick rather than waiting for that sync.
                // Fire the same selection pixel as the normal flow (skip a re-tap of the same pick).
                if (model.id != recoverySelectedModelId.value) {
                    duckChatPixels.fireModelSelected(model.id, pixelSurface)
                }
                duckChatPixels.fireSubmitChangeModel(model.id, pixelSurface)
                recoverySelectedModelId.value = model.id
                effectiveModelProvider.onRecoveryModelPicked(chatId = publishedChatId, modelId = model.id)
                modelChangeChannel.trySend(PickerModelChange.ChangeModel(model.id))
            } else {
                if (model.id != modelManager.getSelectedModelId()) {
                    duckChatPixels.fireModelSelected(model.id, pixelSurface)
                }
                viewModelScope.launch { modelManager.selectModel(model) }
            }
            return
        }
        val modelState = modelManager.modelState.value
        val userTier = modelState.userTier
        val requiredTier = model.requiredTier ?: run {
            logcat { "Duck.ai picker: tapped model has no public required tier (id=${model.id}, accessTier=${model.accessTier}), ignoring." }
            return
        }
        val origin = effectiveOrigin(surface)
        routeUpsell(userTier, requiredTier, origin, modelState.isSubscriptionEligible)?.let { upsell ->
            duckChatPixels.fireSubscriptionUpsellTriggered(
                source = "model_picker",
                currentTier = userTier.toParam(),
                requiredTier = requiredTier.toParam(),
                flowType = upsell.toFlowTypeParam(),
                origin = origin,
            )
            command.trySend(upsell)
        }
    }

    fun updatedPickersEnabled(): Boolean = duckChatFeature.updatedPickers().isEnabled()

    fun buildSections(state: ModelState): List<ModelSection> {
        // Models with a null requiredTier (non-public access tiers only, e.g. "internal") have no
        // section to land in and are intentionally hidden from the picker.
        val public = state.models.filter { it.requiredTier != null }
        if (!updatedPickersEnabled()) {
            val byTier = public.groupBy { it.requiredTier }
            return listOfNotNull(
                byTier[UserTier.FREE].orEmpty().toSectionOrNull(headerRes = null),
                byTier[UserTier.PLUS].orEmpty().toSectionOrNull(R.string.duckAiModelPickerPlusModels),
                byTier[UserTier.PRO].orEmpty().toSectionOrNull(R.string.duckAiModelPickerProModels),
            )
        }
        val (available, gated) = public.partition { it.isAccessible }
        return listOfNotNull(
            available.toSectionOrNull(headerRes = null),
            gated.toSectionOrNull(
                headerRes = gatedSectionHeaderRes(gated.map { it.requiredTier }, state.isFreeTrialEligible),
                gated = true,
            ),
        )
    }

    @StringRes
    fun subtitleResFor(model: AIChatModel): Int? = when (model.label) {
        ModelLabel.EVERYDAY_USE -> R.string.duckAiModelPickerLabelEverydayUse
        ModelLabel.USES_LIMITS_FASTER -> R.string.duckAiModelPickerLabelUsesLimitsFaster
        // A label this version does not know still promotes the model, but we have no copy for it.
        ModelLabel.UNKNOWN, null -> null
    }

    @DrawableRes
    fun getIconResForModel(model: AIChatModel): Int = when (model.provider) {
        ModelProvider.OPENAI -> R.drawable.ic_ai_model_openai_16
        ModelProvider.ANTHROPIC -> R.drawable.ic_ai_model_claude_16
        ModelProvider.MISTRAL -> R.drawable.ic_ai_model_mistral_16
        ModelProvider.META -> R.drawable.ic_ai_model_llama_16
        // OSS doubles as the fallback for unrecognised providers so a model never renders without an icon.
        ModelProvider.OSS, ModelProvider.UNKNOWN -> R.drawable.ic_ai_model_oss_16
    }

    private fun List<AIChatModel>.toSectionOrNull(
        @StringRes headerRes: Int?,
        gated: Boolean = false,
    ): ModelSection? = takeIf { it.isNotEmpty() }?.let { ModelSection(headerRes, it, gated) }
}
