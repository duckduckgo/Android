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

package com.duckduckgo.duckchat.impl.nativeinput

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.RealDuckChat
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class NewChat(
    val isNewChatPage: Boolean,
    val isInputReady: Boolean,
) {
    val isGuaranteed: Boolean get() = isNewChatPage && isInputReady
}

class NewChatDetector @Inject constructor(
    private val provider: NativeInputStateProvider,
    private val chatStateStore: ChatStateStore,
    private val voiceSessions: VoiceSessionStateManager,
    private val tabRepository: TabRepository,
    private val duckChat: DuckChatInternal,
    private val chatStore: DuckAiChatStore,
    private val dispatchers: DispatcherProvider,
) {

    // chatId alone can't tell: it stays null until an opened chat's page loads, while a prompt is being handed over, and
    // for voice chats. The frontend reports ready on every page load and hides the input during voice.
    fun observe(tabId: String): Flow<NewChat> = combine(
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
            // Every tab update re-emits the whole list, so the lookup stays off the main thread.
            tabRepository.flowTabs.map { tabs -> tabs.firstOrNull { it.tabId == tabId }?.url?.toUri() }.distinctUntilChanged().flowOn(
                dispatchers.io(),
            ),
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

    private data class ChatPage(
        val isVoiceMode: Boolean,
        val isSendingPrompt: Boolean,
        val isVoiceChat: Boolean,
    )

    // A model-change window lets the input use a recovery model instead of the selected one, which a hint can't switch away from.
    private fun NativeInputState.isNewChat() = inputContext == InputContext.DUCK_AI && chatId == null && !modelChangeMode

    private companion object {
        const val VOICE_CHAT_ID_GRACE_MS = 1_000L
    }
}
