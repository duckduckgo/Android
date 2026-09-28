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
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.AvailableReasoningMode
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.ReasoningMode
import com.duckduckgo.duckchat.impl.models.ReasoningResolver
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

data class ReasoningModeRow(
    val mode: ReasoningMode,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val selected: Boolean,
)

data class ReasoningSection(
    @StringRes val headerRes: Int?,
    val rows: List<ReasoningModeRow>,
    /** Set on the gated section, for the upsell impression pixel. */
    val gatedHeader: GatedHeader? = null,
)

/** Resolved snapshot the picker view renders from. */
data class ReasoningModePickerState(
    val visible: Boolean,
    val sections: List<ReasoningSection>,
    val displayedMode: ReasoningMode?,
) {
    val rows: List<ReasoningModeRow> get() = sections.flatMap { it.rows }
}

@ContributesViewModel(ViewScope::class)
class ReasoningModePickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val duckAiChatStore: DuckAiChatStore,
    private val duckChatPixels: DuckChatPixels,
    private val duckChatFeature: DuckChatFeature,
) : ViewModel() {

    private val currentChat = MutableStateFlow<DuckAiChat?>(null)

    // The pixel surface for the active tab, tracked from the published input context.
    // Named distinctly from the [PickerSurface] param on [onModeTapped] to avoid shadowing.
    private var pixelSurface: DuckChatPixelSurface = DuckChatPixelSurface.ADDRESS_BAR

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
            nativeInputStateProvider.state.collect { pixelSurface = DuckChatPixelSurface.from(it.inputContext) }
        }
    }

    /** Existing chat: rows derive from the chat's model. Otherwise: from the global selection. */
    val state: StateFlow<ReasoningModePickerState> = combine(
        modelManager.modelState,
        nativeInputStateProvider.state,
        currentChat,
    ) { modelState, nativeState, chat ->
        resolveState(modelState, nativeState, chat)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReasoningModePickerState(visible = false, sections = emptyList(), displayedMode = null),
    )

    private fun resolveState(
        modelState: ModelState,
        nativeState: NativeInputState,
        chat: DuckAiChat?,
    ): ReasoningModePickerState {
        val activeChat = chat?.takeIf { it.chatId == nativeState.chatId }
        val chatResolution = activeChat?.let { ReasoningResolver.forChat(it, modelState) }
        if (nativeState.chatId != null && chatResolution == null) {
            return ReasoningModePickerState(visible = false, sections = emptyList(), displayedMode = null)
        }
        val available = chatResolution?.available ?: modelState.availableReasoningModes
        val persistedMode = chatResolution?.mode ?: modelState.selectedReasoningMode
        val displayedMode = ReasoningResolver.resolveMode(persistedMode, available)
        val visible = available.size > 1 && available.any { it.isAccessible }
        val sections = buildSections(available, displayedMode, modelState)
        return ReasoningModePickerState(visible = visible, sections = sections, displayedMode = displayedMode)
    }

    private fun buildSections(
        available: List<AvailableReasoningMode>,
        displayedMode: ReasoningMode?,
        modelState: ModelState,
    ): List<ReasoningSection> {
        fun List<AvailableReasoningMode>.toRows() = map { it.toRow(selected = it.mode == displayedMode) }
        if (!duckChatFeature.updatedPickers().isEnabled()) {
            return listOf(ReasoningSection(headerRes = null, rows = available.toRows()))
        }
        val (accessible, gated) = available.partition { it.isAccessible }
        return listOfNotNull(
            accessible.takeIf { it.isNotEmpty() }?.let { ReasoningSection(headerRes = null, rows = it.toRows()) },
            gated.takeIf { it.isNotEmpty() }?.let {
                val header = gatedSectionHeader(it.map { mode -> mode.access?.requiredTier }, modelState.isFreeTrialEligible)
                ReasoningSection(headerRes = header.titleRes, rows = it.toRows(), gatedHeader = header)
            },
        )
    }

    private val command = Channel<UpsellCommand>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<UpsellCommand> = command.receiveAsFlow()

    fun onPickerShown(surface: PickerSurface) {
        duckChatPixels.fireReasoningEffortPickerShown(surface.origin)
        state.value.sections.firstNotNullOfOrNull { it.gatedHeader }?.let { header ->
            duckChatPixels.firePickerUpsellShown(
                source = UPSELL_SOURCE_REASONING_PICKER,
                header = header.pixelValue,
                currentTier = modelManager.modelState.value.userTier.toParam(),
                origin = surface.origin,
            )
        }
    }

    fun onModeTapped(mode: ReasoningMode, surface: PickerSurface) {
        // Drop taps that race past the picker hiding (loading, mismatch, no accessible rows).
        if (!state.value.visible) return
        val modelState = modelManager.modelState.value
        val chat = currentChat.value
        val chatResolution = chat?.let { ReasoningResolver.forChat(it, modelState) }
        val available = chatResolution?.available ?: modelState.availableReasoningModes

        val match = available.firstOrNull { it.mode == mode }
        if (match == null) {
            logcat { "Duck.ai reasoning picker: tapped mode $mode not in available list, ignoring." }
            return
        }
        if (match.isAccessible) {
            // Mirror the model picker: only report a selection when it actually changes, not on
            // re-tapping the already-selected effort. The persisted selection still updates below.
            if (mode != state.value.displayedMode) {
                duckChatPixels.fireReasoningEffortSelected(mode.toEffortParam(), pixelSurface)
            }
            if (chatResolution != null) {
                viewModelScope.launch { modelManager.setChatScopedReasoningMode(mode) }
            } else {
                viewModelScope.launch { modelManager.selectReasoningMode(mode) }
            }
            return
        }
        val userTier = modelState.userTier
        val requiredTier = match.access?.requiredTier ?: run {
            logcat { "Duck.ai reasoning picker: gated mode $mode has no public required tier, ignoring." }
            return
        }
        routeUpsell(userTier, requiredTier, surface.origin, modelState.isSubscriptionEligible)?.let { upsell ->
            duckChatPixels.fireSubscriptionUpsellTriggered(
                source = UPSELL_SOURCE_REASONING_PICKER,
                currentTier = userTier.toParam(),
                requiredTier = requiredTier.toParam(),
                flowType = upsell.toFlowTypeParam(),
                origin = surface.origin,
            )
            command.trySend(upsell)
        }
    }

    private fun ReasoningMode.toEffortParam(): String = when (this) {
        ReasoningMode.FAST -> "fast"
        ReasoningMode.REASONING -> "reasoning"
        ReasoningMode.EXTENDED_REASONING -> "extended_reasoning"
    }

    @DrawableRes
    fun iconResFor(mode: ReasoningMode): Int = when (mode) {
        ReasoningMode.FAST -> R.drawable.ic_reasoning_fast_24
        ReasoningMode.REASONING -> R.drawable.ic_reasoning_thinking_24
        ReasoningMode.EXTENDED_REASONING -> R.drawable.ic_reasoning_extended_24
    }

    private fun AvailableReasoningMode.toRow(selected: Boolean): ReasoningModeRow {
        val (titleRes, subtitleRes) = when (mode) {
            ReasoningMode.FAST ->
                R.string.duckChatReasoningModeFastTitle to R.string.duckChatReasoningModeFastSubtitle
            ReasoningMode.REASONING ->
                R.string.duckChatReasoningModeReasoningTitle to R.string.duckChatReasoningModeReasoningSubtitle
            ReasoningMode.EXTENDED_REASONING ->
                R.string.duckChatReasoningModeExtendedTitle to R.string.duckChatReasoningModeExtendedSubtitle
        }
        return ReasoningModeRow(
            mode = mode,
            titleRes = titleRes,
            subtitleRes = subtitleRes,
            iconRes = iconResFor(mode),
            selected = selected,
        )
    }
}
