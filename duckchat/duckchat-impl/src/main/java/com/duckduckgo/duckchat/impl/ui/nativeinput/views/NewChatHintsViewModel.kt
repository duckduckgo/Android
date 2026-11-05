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
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsEligibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import kotlin.random.Random

@ContributesViewModel(ViewScope::class)
class NewChatHintsViewModel @Inject constructor(
    private val provider: NativeInputStateProvider,
    private val modelManager: DuckAiModelManager,
    private val duckChat: DuckChatInternal,
    private val tabRepository: TabRepository,
    eligibility: NewChatHintsEligibility,
    browserMode: BrowserMode,
) : ViewModel() {

    private val eligible = eligibility.observeEligible(browserMode)

    fun viewState(tabId: String): Flow<List<NewChatHint>> {
        val order = hintOrder(tabId)
        return combine(
            tabRepository.flowSelectedTab,
            provider.stateForTab(tabId),
            modelManager.modelState,
            eligible,
        ) { selectedTab, input, models, eligible ->
            val showHints = selectedTab?.tabId == tabId && eligible && input.isNewChat()
            if (showHints) order.filter { capableModel(it, models) != null } else emptyList()
        }.distinctUntilChanged()
    }

    private fun capableModel(hint: NewChatHint, models: ModelState): AIChatModel? {
        if (hint == NewChatHint.PHOTO && !duckChat.isImageUploadEnabled()) return null
        val capable = models.models.filter { it.isAccessible && hint.supportedBy(it) }
        return capable.firstOrNull { it.id == models.selectedModelId } ?: capable.firstOrNull()
    }

    private fun hintOrder(tabId: String): List<NewChatHint> = NewChatHint.entries.shuffled(Random(tabId.hashCode()))

    private fun NativeInputState.isNewChat() = inputContext == InputContext.DUCK_AI && chatId == null
}
