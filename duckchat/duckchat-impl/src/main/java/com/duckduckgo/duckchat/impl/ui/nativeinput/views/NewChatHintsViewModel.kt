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
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHint
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommand
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommands
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsEligibility
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.random.Random

@ContributesViewModel(ViewScope::class)
class NewChatHintsViewModel @Inject constructor(
    private val provider: NativeInputStateProvider,
    private val modelManager: DuckAiModelManager,
    private val duckChat: DuckChatInternal,
    private val commands: NewChatHintCommands,
    private val tabRepository: TabRepository,
    private val usage: NewChatHintsUsage,
    eligibility: NewChatHintsEligibility,
    private val browserMode: BrowserMode,
) : ViewModel() {

    private val eligible: StateFlow<Boolean?> = eligibility.observeEligible(browserMode).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var isHandlingTap = false

    fun viewState(tabId: String): Flow<List<NewChatHint>> {
        val order = hintOrder(tabId)
        return combine(
            tabRepository.flowSelectedTab,
            provider.stateForTab(tabId),
            modelManager.modelState,
            eligible.filterNotNull(),
        ) { selectedTab, input, models, isEligible ->
            val showHints = selectedTab?.tabId == tabId && isEligible && input.isNewChat() && input.isDraftEmpty()
            if (showHints) order.filter { capableModel(it, models) != null } else emptyList()
        }.distinctUntilChanged()
            .onEach { hints -> if (hints.isNotEmpty() && browserMode == BrowserMode.REGULAR) usage.onHintsDisplayed(tabId) }
    }

    suspend fun onHintTapped(hint: NewChatHint, tabId: String) {
        if (isHandlingTap) return
        if (browserMode == BrowserMode.REGULAR) usage.onHintTapped(tabId)
        isHandlingTap = true
        try {
            sendCommand(hint, tabId)
        } finally {
            isHandlingTap = false
        }
    }

    private suspend fun sendCommand(hint: NewChatHint, tabId: String) {
        if (!canAct(tabId)) return
        val models = modelManager.modelState.value
        val model = capableModel(hint, models) ?: return
        if (model.id != models.selectedModelId) {
            modelManager.selectModel(model)
            // Selecting a model suspends; the tab, chat or eligibility may have changed meanwhile.
            if (!canAct(tabId)) return
        }
        val command = when (hint) {
            NewChatHint.DOCUMENTS -> NewChatHintCommand.OpenDocuments(tabId, model.supportedFileTypes)
            NewChatHint.PHOTO -> NewChatHintCommand.OpenPhoto(tabId)
            NewChatHint.IMAGE_GENERATION -> NewChatHintCommand.CreateImage(tabId)
        }
        commands.send(command)
    }

    private fun capableModel(hint: NewChatHint, models: ModelState): AIChatModel? {
        if (hint == NewChatHint.PHOTO && !duckChat.isImageUploadEnabled()) return null
        val capable = models.models.filter { it.isAccessible && hint.supportedBy(it) }
        return capable.firstOrNull { it.id == models.selectedModelId } ?: capable.firstOrNull()
    }

    private suspend fun canAct(tabId: String): Boolean =
        eligible.value == true &&
            tabRepository.flowSelectedTab.firstOrNull()?.tabId == tabId &&
            provider.stateForTab(tabId).value.isNewChat()

    private fun hintOrder(tabId: String): List<NewChatHint> = NewChatHint.entries.shuffled(Random(tabId.hashCode()))

    // A model-change window lets the input use a recovery model instead of the selected one, which a hint can't switch away from.
    private fun NativeInputState.isNewChat() = inputContext == InputContext.DUCK_AI && chatId == null && !modelChangeMode

    private fun NativeInputState.isDraftEmpty() = !hasText && !hasAttachments && selectedTool == null
}
