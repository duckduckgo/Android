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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiChatsMenu
import com.duckduckgo.duckchat.api.DuckChatHistoryNoParams
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class RealDuckAiChatsMenu @Inject constructor(
    private val activity: AppCompatActivity,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val duckChat: DuckChatInternal,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
) : DuckAiChatsMenu {

    override suspend fun show(
        anchorView: View,
        options: DuckAiChatsMenu.Options,
        listener: DuckAiChatsMenu.Listener,
    ) {
        val recentChats = withContext(dispatchers.io()) {
            chatHistoryRepository.observeChats().firstOrNull().orEmpty()
                .sortedByDescending { it.lastEditMillis }
                .take(MAX_RECENT_CHATS)
        }
        DuckAiChatsPopupMenu(
            layoutInflater = activity.layoutInflater,
            recentChats = recentChats,
            onNewChat = if (options.showNewChat) listener::onNewChatSelected else null,
            onOpenDuckAi = null,
            onRecentChat = { chatId -> listener.onChatSelected(duckChat.buildChatUrl(chatId)) },
            onViewAllChats = { globalActivityStarter.start(activity, DuckChatHistoryNoParams) },
            showRecentChatsHeader = options.showRecentChatsHeader,
        ).show(activity, activity.window.decorView, anchorView)
    }

    private companion object {
        const val MAX_RECENT_CHATS = 5
    }
}
