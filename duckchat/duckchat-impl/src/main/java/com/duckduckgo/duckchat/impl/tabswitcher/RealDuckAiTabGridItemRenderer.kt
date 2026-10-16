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

package com.duckduckgo.duckchat.impl.tabswitcher

import android.content.Context
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemContent
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemRenderer
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemView
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatPreview
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
@SingleIn(ActivityScope::class)
class RealDuckAiTabGridItemRenderer @Inject constructor(
    @param:RegularMode private val regularChatStore: DuckAiChatStore,
    @param:FireMode private val fireChatStore: DuckAiChatStore,
    private val duckChatFeature: DuckChatFeature,
    private val voiceSessionStateManager: VoiceSessionStateManager,
    private val duckChat: DuckChat,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiTabGridItemRenderer {

    // Lives as long as the tab switcher, so each chat is read and parsed once per opening and shows its state at that point.
    private val chatPreviews = ConcurrentHashMap<Pair<BrowserMode, String>, DuckAiChatPreview>()

    // A chat that can't be read, such as a row over the CursorWindow limit, falls back to the host's card instead of failing the whole list.
    override suspend fun resolve(tabId: String, url: String?, mode: BrowserMode): DuckAiTabGridItemContent? = withContext(dispatchers.io()) {
        try {
            resolveContent(tabId, url, mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai tab content failed to resolve: ${e.asLog()}" }
            null
        }
    }

    private suspend fun resolveContent(tabId: String, url: String?, mode: BrowserMode): DuckAiTabGridItemContent? {
        if (voiceSessionStateManager.isVoiceSessionActive(tabId)) return RealDuckAiTabGridItemContent.ActiveVoice

        val chatId = url?.toUri()?.toChatIdOrNull(duckChat) ?: return RealDuckAiTabGridItemContent.Empty

        val store = storeFor(mode)
        if (!store.hasMigrated() || !duckChatFeature.useNativeStorageChatData().isEnabled()) return null
        return chatPreview(store, mode, chatId)?.toContent(mode)
    }

    override fun create(context: Context): DuckAiTabGridItemView = DuckAiTabGridCardView(
        context = context,
        thumbnailLoader = { fileRef, mode -> storeFor(mode).readFileRef(fileRef)?.bytes },
        dispatchers = dispatchers,
        coroutineScope = appCoroutineScope,
    )

    private suspend fun chatPreview(store: DuckAiChatStore, mode: BrowserMode, chatId: String): DuckAiChatPreview? {
        val key = mode to chatId
        return chatPreviews[key] ?: store.getChatPreview(chatId)?.also { chatPreviews[key] = it }
    }

    private fun storeFor(mode: BrowserMode): DuckAiChatStore = when (mode) {
        BrowserMode.REGULAR -> regularChatStore
        BrowserMode.FIRE -> fireChatStore
    }

    // A chat with nothing to preview shows the empty card rather than falling back to the host's card.
    private fun DuckAiChatPreview.toContent(mode: BrowserMode): RealDuckAiTabGridItemContent {
        val title = chat.title
        if (chat.isImageGeneration) {
            val fileRef = chat.fileRefs.lastOrNull() ?: return RealDuckAiTabGridItemContent.Empty
            return RealDuckAiTabGridItemContent.ImageChat(title, fileRef, mode)
        }
        // Long chats would otherwise copy their whole last reply into every card.
        val snippet = lastMessageText?.take(SNIPPET_CHARACTER_CAP)
        if (chat.isVoice) {
            return snippet?.let { RealDuckAiTabGridItemContent.VoiceTranscript(title, it) } ?: RealDuckAiTabGridItemContent.Empty
        }
        if (isAwaitingReply) return RealDuckAiTabGridItemContent.Thinking(title)
        return snippet?.let { RealDuckAiTabGridItemContent.Chat(title, it) } ?: RealDuckAiTabGridItemContent.Empty
    }

    private companion object {
        const val SNIPPET_CHARACTER_CAP = 500
    }
}
