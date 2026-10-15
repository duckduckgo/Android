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

package com.duckduckgo.duckchat.impl.history

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiChatsMenu
import com.duckduckgo.duckchat.api.DuckChatHistoryParams
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class RealDuckAiChatsMenu @Inject constructor(
    private val activity: AppCompatActivity,
    chatHistoryRepository: ChatHistoryRepository,
    private val duckChat: DuckChatInternal,
    private val globalActivityStarter: GlobalActivityStarter,
    dispatchers: DispatcherProvider,
) : DuckAiChatsMenu {

    // Loaded from creation so show() doesn't wait on parsing the chat history when the user taps.
    private val recentChats: StateFlow<List<ChatHistoryItem>?> = chatHistoryRepository.observeChats()
        .map { chats -> chats.sortedByDescending { it.lastEditMillis }.take(MAX_RECENT_CHATS) }
        .flowOn(dispatchers.io())
        .stateIn(activity.lifecycleScope, SharingStarted.Eagerly, null)

    override suspend fun show(
        anchorView: View,
        options: DuckAiChatsMenu.Options,
        listener: DuckAiChatsMenu.Listener,
    ) {
        val recentChats = recentChats.filterNotNull().firstOrNull().orEmpty()
        DuckAiChatsPopupMenu(
            layoutInflater = activity.layoutInflater,
            recentChats = recentChats,
            onNewChat = if (options.showNewChat) listener::onNewChatSelected else null,
            onOpenDuckAi = null,
            onRecentChat = { chatId -> listener.onChatSelected(duckChat.buildChatUrl(chatId)) },
            onViewAllChats = { globalActivityStarter.start(activity, DuckChatHistoryParams(options.chatHistorySource)) },
            showRecentChatsHeader = options.menuStyle == DuckAiChatsMenu.ChatsMenuStyle.FULL,
        ).show(activity, activity.window.decorView, anchorView)
    }

    private companion object {
        const val MAX_RECENT_CHATS = 5
    }
}
