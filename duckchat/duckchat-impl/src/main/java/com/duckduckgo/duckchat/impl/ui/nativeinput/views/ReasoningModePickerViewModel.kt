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
import com.duckduckgo.duckchat.impl.models.AvailableReasoningMode
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.ReasoningMode
import com.duckduckgo.duckchat.impl.models.ReasoningResolver
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

@ContributesViewModel(ViewScope::class)
class ReasoningModePickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    fun resolvedMode(state: ModelState): ReasoningMode? =
        ReasoningResolver.resolveMode(state.selectedReasoningMode, state.availableReasoningModes)

    private val command = Channel<UpsellCommand>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<UpsellCommand> = command.receiveAsFlow()

    fun onModeTapped(mode: ReasoningMode, surface: PickerSurface) {
        val available = modelManager.modelState.value.availableReasoningModes.firstOrNull { it.mode == mode }
        if (available == null) {
            logcat { "Duck.ai reasoning picker: tapped mode $mode not in available list, ignoring." }
            return
        }
        if (available.isAccessible) {
            viewModelScope.launch { modelManager.selectReasoningMode(mode) }
            return
        }
        val userTier = modelManager.modelState.value.userTier
        val requiredTier = available.access?.requiredTier ?: run {
            logcat { "Duck.ai reasoning picker: gated mode $mode has no public required tier, ignoring." }
            return
        }
        routeUpsell(userTier, requiredTier, surface.origin)?.let { command.trySend(it) }
    }

    fun rows(state: ModelState): List<ReasoningModeRow> {
        val resolved = resolvedMode(state)
        return state.availableReasoningModes.map { it.toRow(selected = it.mode == resolved) }
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
