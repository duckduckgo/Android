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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

class ChatHistoryViewHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {

    private val typeIcon: ImageView = itemView.findViewById(R.id.chatHistoryTypeIcon)
    private val title: DaxTextView = itemView.findViewById(R.id.chatHistoryTitle)
    private val moreButton: ImageView = itemView.findViewById(R.id.chatHistoryMore)

    fun bind(
        item: ChatHistoryItem,
        onClick: (ChatHistoryItem) -> Unit,
        onMoreClick: (ChatHistoryItem, View) -> Unit,
        onLongClick: (ChatHistoryItem) -> Boolean = { false },
    ) {
        title.text = item.displayTitle
        typeIcon.setImageResource(iconFor(item.type, item.pinned))
        itemView.setOnClickListener { onClick(item) }
        itemView.setOnLongClickListener { onLongClick(item) }
        moreButton.setOnClickListener { anchor -> onMoreClick(item, anchor) }
    }

    private fun iconFor(type: ChatType, pinned: Boolean): Int = when (type) {
        ChatType.Discussion -> if (pinned) R.drawable.ic_chat_pin_24 else R.drawable.ic_chat_24
        ChatType.ImageGeneration -> if (pinned) R.drawable.ic_images_pin_24 else R.drawable.ic_images_24
        ChatType.Voice -> if (pinned) R.drawable.ic_voice_pin_24 else R.drawable.ic_voice_24
    }

    companion object {
        fun create(parent: ViewGroup): ChatHistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_chat_history_item, parent, false)
            return ChatHistoryViewHolder(view)
        }
    }
}
