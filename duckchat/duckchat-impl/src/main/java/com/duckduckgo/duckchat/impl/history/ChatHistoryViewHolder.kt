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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.iconRes

class ChatHistoryViewHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {

    private val iconContainer: FrameLayout = itemView.findViewById(R.id.chatHistoryIconContainer)
    private val typeIcon: ImageView = itemView.findViewById(R.id.chatHistoryTypeIcon)
    private val title: DaxTextView = itemView.findViewById(R.id.chatHistoryTitle)
    private val moreButton: ImageView = itemView.findViewById(R.id.chatHistoryMore)

    fun bind(
        item: ChatHistoryItem,
        selected: Boolean,
        onClick: (ChatHistoryItem) -> Unit,
        onMoreClick: (ChatHistoryItem, View) -> Unit,
        onLongClick: (ChatHistoryItem) -> Boolean = { false },
    ) {
        iconContainer.animate().cancel()
        iconContainer.rotationY = 0f
        title.text = item.displayTitle
        applySelectionState(item, selected)
        itemView.setOnClickListener { onClick(item) }
        itemView.setOnLongClickListener { onLongClick(item) }
        moreButton.setOnClickListener { anchor -> onMoreClick(item, anchor) }
    }

    fun animateSelectionChange(item: ChatHistoryItem, selected: Boolean) {
        iconContainer.animate().cancel()
        iconContainer.animate()
            .rotationY(HALF_FLIP_DEGREES)
            .setDuration(HALF_FLIP_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                applySelectionState(item, selected)
                iconContainer.rotationY = -HALF_FLIP_DEGREES
                iconContainer.animate()
                    .rotationY(0f)
                    .setDuration(HALF_FLIP_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun applySelectionState(item: ChatHistoryItem, selected: Boolean) {
        if (selected) {
            iconContainer.setBackgroundResource(R.drawable.bg_chat_history_circle_accent)
            typeIcon.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
            typeIcon.setColorFilter(itemView.context.getColor(com.duckduckgo.mobile.android.R.color.white))
            iconContainer.contentDescription = itemView.context.getString(R.string.duck_ai_chat_history_row_selected_content_description)
        } else {
            iconContainer.setBackgroundResource(R.drawable.bg_chat_history_circle_solid)
            typeIcon.setImageResource(item.type.iconRes(item.pinned))
            typeIcon.colorFilter = null
            iconContainer.contentDescription = null
        }
    }

    companion object {
        private const val HALF_FLIP_DEGREES = 90f
        private const val HALF_FLIP_DURATION_MS = 150L

        fun create(parent: ViewGroup): ChatHistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_chat_history_item, parent, false)
            return ChatHistoryViewHolder(view)
        }
    }
}
