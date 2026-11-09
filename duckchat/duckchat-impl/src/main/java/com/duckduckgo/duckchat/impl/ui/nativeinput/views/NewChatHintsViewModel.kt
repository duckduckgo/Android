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

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.RealDuckChat
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.ChatStateStore
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHint
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommand
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommands
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsEligibility
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsUsage
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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
    private val chatStateStore: ChatStateStore,
    private val voiceSessions: VoiceSessionStateManager,
    private val chatStore: DuckAiChatStore,
) : ViewModel() {

    private val eligible: StateFlow<Boolean?> = eligibility.observeEligible(browserMode).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var isHandlingTap = false
    private val newChats = mutableMapOf<String, Flow<NewChat>>()

    fun viewState(tabId: String): Flow<List<NewChatHint>> {
        val order = hintOrder(tabId)
        // Only a change seen while collecting counts as leaving: a view re-attaching can briefly read a reloading page as not new.
        // The frontend hiding the input on the same page isn't leaving either, or a tap before it would be forgotten.
        var wasNewChat = false
        val newChat = newChat(tabId)
            .onEach { newChat ->
                if (wasNewChat && !newChat.isNewChatPage && browserMode == BrowserMode.REGULAR) usage.onLeftNewChat(tabId)
                wasNewChat = newChat.isGuaranteed || (wasNewChat && newChat.isNewChatPage)
            }
        return combine(
            tabRepository.flowSelectedTab,
            provider.stateForTab(tabId),
            modelManager.modelState,
            eligible.filterNotNull(),
            newChat,
        ) { selectedTab, input, models, isEligible, newChat ->
            val showHints = selectedTab?.tabId == tabId && isEligible && newChat.isGuaranteed && input.isDraftEmpty()
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
            newChat(tabId).firstOrNull()?.isGuaranteed == true

    private fun hintOrder(tabId: String): List<NewChatHint> = NewChatHint.entries.shuffled(Random(tabId.hashCode()))

    // Shared so a tap checks the same remembered voice state as the hints on screen, not a fresh read of the URL.
    private fun newChat(tabId: String): Flow<NewChat> = newChats.getOrPut(tabId) {
        observeNewChat(tabId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
    }

    // chatId alone can't tell: it stays null until an opened chat's page loads, while a prompt is being handed over, and
    // for voice chats. The frontend reports ready on every page load and hides the input during voice.
    private fun observeNewChat(tabId: String): Flow<NewChat> = combine(
        provider.stateForTab(tabId),
        chatStateStore.stateFor(tabId),
        voiceSessions.activeVoiceSessions,
        chatPage(tabId),
    ) { input, chatState, voiceTabs, page ->
        NewChat(
            isNewChatPage = input.isNewChat() && tabId !in voiceTabs && !page.isVoiceMode && !page.isSendingPrompt && !page.isVoiceChat,
            isInputReady = chatState == ChatState.READY || chatState == ChatState.SHOW,
        )
    }.distinctUntilChanged()

    private fun chatPage(tabId: String): Flow<ChatPage> = flow {
        var isVoiceMode = false
        var isVoiceInputBack = false
        val pageChanges = combine(
            tabRepository.flowTabs.map { tabs -> tabs.firstOrNull { it.tabId == tabId }?.url?.toUri() }.distinctUntilChanged(),
            chatStateStore.stateFor(tabId),
        ) { url, chatState -> url to chatState }
        emitAll(
            pageChanges.transformLatest { (url, chatState) ->
                val chatId = url?.toChatIdOrNull(duckChat)
                val isVoiceModeUrl = url?.let(RealDuckChat::isVoiceModeUrl) == true
                // The frontend drops the voice-mode param once it loads and keeps the input hidden until voice ends.
                isVoiceMode = isVoiceModeUrl || (isVoiceMode && chatId == null)
                isVoiceInputBack = isVoiceMode && !isVoiceModeUrl && (chatState == ChatState.SHOW || isVoiceInputBack)
                val page = ChatPage(
                    isVoiceMode = isVoiceMode,
                    isSendingPrompt = chatId == null && url?.let(RealDuckChat::isPromptHandoverUrl) == true,
                    // A chat opened from history: the input reports no chatId for voice chats, so only the stored chat tells.
                    isVoiceChat = chatId?.let { chatStore.getChatById(it)?.isVoice } == true,
                )
                emit(page)
                // A finished voice chat shows the input again just before its id reaches the URL; a cancelled one never gets an id.
                if (isVoiceInputBack) {
                    delay(VOICE_CHAT_ID_GRACE_MS.milliseconds)
                    isVoiceMode = false
                    isVoiceInputBack = false
                    emit(page.copy(isVoiceMode = false))
                }
            },
        )
    }.distinctUntilChanged()

    private data class NewChat(
        val isNewChatPage: Boolean,
        val isInputReady: Boolean,
    ) {
        val isGuaranteed: Boolean get() = isNewChatPage && isInputReady
    }

    private data class ChatPage(
        val isVoiceMode: Boolean,
        val isSendingPrompt: Boolean,
        val isVoiceChat: Boolean,
    )

    // A model-change window lets the input use a recovery model instead of the selected one, which a hint can't switch away from.
    private fun NativeInputState.isNewChat() = inputContext == InputContext.DUCK_AI && chatId == null && !modelChangeMode

    private fun NativeInputState.isDraftEmpty() = !hasText && !hasAttachments && selectedTool == null

    private companion object {
        const val VOICE_CHAT_ID_GRACE_MS = 1_000L
    }
}
