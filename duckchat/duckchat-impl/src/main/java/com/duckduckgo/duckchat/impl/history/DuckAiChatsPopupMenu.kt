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

import android.app.Activity
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.iconRes

/**
 * Popup listing [recentChats] between optional New Chat / Open Duck.ai rows and a View all footer.
 * A nullable action hides its row; the caller owns pixels and navigation.
 */
class DuckAiChatsPopupMenu(
    private val layoutInflater: LayoutInflater,
    private val recentChats: List<ChatHistoryItem>,
    private val onNewChat: (() -> Unit)?,
    private val onOpenDuckAi: (() -> Unit)?,
    private val onRecentChat: (chatId: String) -> Unit,
    private val onViewAllChats: () -> Unit,
    private val showRecentChatsHeader: Boolean = true,
) {

    fun show(
        activity: Activity,
        rootView: View,
        anchorView: View,
    ): PopupMenu {
        val popup = PopupMenu(
            layoutInflater = layoutInflater,
            resourceId = R.layout.popup_contextual_chats_menu,
            width = layoutInflater.context.resources.getDimensionPixelSize(R.dimen.contextualChatsPopupMenuWidth),
        )
        val content = popup.contentView

        content.findViewById<PopupMenuItemView>(R.id.contextualChatsPopupNewChat).bindOptional(popup, onNewChat)
        content.findViewById<View>(R.id.contextualChatsPopupHeaderDivider).isVisible = onNewChat != null
        content.findViewById<PopupMenuItemView>(R.id.contextualChatsPopupOpenDuckAi).bindOptional(popup, onOpenDuckAi)

        val hasRecentChats = recentChats.isNotEmpty()
        content.findViewById<View>(R.id.contextualChatsPopupRecentHeader).isVisible = showRecentChatsHeader && hasRecentChats
        content.findViewById<View>(R.id.contextualChatsPopupFooterDivider).isVisible = hasRecentChats
        val recentContainer = content.findViewById<LinearLayout>(R.id.contextualChatsPopupRecentContainer)
        recentChats.forEach { chat ->
            val row = PopupMenuItemView(layoutInflater.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setPrimaryText(chat.displayTitle)
                setLeadingIconResource(chat.type.iconRes(chat.pinned))
                setPrimaryTextMaxLines(MAX_CHAT_TITLE_LINES)
                setPrimaryTextEllipsize(TextUtils.TruncateAt.END)
            }
            popup.onMenuItemClicked(row) { onRecentChat(chat.chatId) }
            recentContainer.addView(row)
        }

        popup.onMenuItemClicked(content.findViewById(R.id.contextualChatsPopupViewAll), onViewAllChats)

        popup.showAnchoredView(activity, rootView, anchorView)
        return popup
    }

    private fun PopupMenuItemView.bindOptional(
        popup: PopupMenu,
        onClick: (() -> Unit)?,
    ) {
        isVisible = onClick != null
        onClick?.let { popup.onMenuItemClicked(this, it) }
    }

    private companion object {
        const val MAX_CHAT_TITLE_LINES = 1
    }
}
