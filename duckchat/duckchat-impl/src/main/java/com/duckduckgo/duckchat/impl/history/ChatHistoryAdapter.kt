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

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ChatHistoryAdapter(
    private val onChatClicked: (ChatHistoryItem) -> Unit,
    private val onChatFireClicked: (ChatHistoryItem) -> Unit,
    private val onChatMoreClicked: (ChatHistoryItem, android.view.View) -> Unit,
    private val onChatLongClicked: (ChatHistoryItem) -> Boolean = { false },
) : ListAdapter<ChatHistoryListEntry, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatHistoryListEntry.Header -> VIEW_TYPE_HEADER
        is ChatHistoryListEntry.Row -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_HEADER -> ChatHistorySectionHeaderViewHolder.create(parent)
        VIEW_TYPE_ROW -> ChatHistoryViewHolder.create(parent)
        else -> error("Unknown viewType=$viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = getItem(position)) {
            is ChatHistoryListEntry.Header -> (holder as ChatHistorySectionHeaderViewHolder).bind(entry.labelRes)
            is ChatHistoryListEntry.Row -> (holder as ChatHistoryViewHolder).bind(
                item = entry.item,
                selected = entry.selected,
                showFireButton = !entry.selectMode,
                onClick = onChatClicked,
                onFireClick = onChatFireClicked,
                onMoreClick = onChatMoreClicked,
                onLongClick = onChatLongClicked,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val selectionChange = payloads.firstOrNull { it is RowChange.SelectionChanged } as? RowChange.SelectionChanged
        val selectModeChanged = payloads.any { it is RowChange.SelectModeChanged }
        val entry = getItem(position)
        if ((selectionChange != null || selectModeChanged) && holder is ChatHistoryViewHolder && entry is ChatHistoryListEntry.Row) {
            selectionChange?.let { holder.animateSelectionChange(entry.item, it.selected) }
            holder.setFireButtonVisible(!entry.selectMode)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private sealed interface RowChange {
        data class SelectionChanged(val selected: Boolean) : RowChange
        data object SelectModeChanged : RowChange
    }

    private object Diff : DiffUtil.ItemCallback<ChatHistoryListEntry>() {
        override fun areItemsTheSame(oldItem: ChatHistoryListEntry, newItem: ChatHistoryListEntry): Boolean = when {
            oldItem is ChatHistoryListEntry.Header && newItem is ChatHistoryListEntry.Header ->
                oldItem.labelRes == newItem.labelRes
            oldItem is ChatHistoryListEntry.Row && newItem is ChatHistoryListEntry.Row ->
                oldItem.item.chatId == newItem.item.chatId
            else -> false
        }

        override fun areContentsTheSame(oldItem: ChatHistoryListEntry, newItem: ChatHistoryListEntry): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: ChatHistoryListEntry, newItem: ChatHistoryListEntry): Any? {
            if (oldItem is ChatHistoryListEntry.Row && newItem is ChatHistoryListEntry.Row &&
                oldItem.item == newItem.item && oldItem.selected != newItem.selected
            ) {
                return RowChange.SelectionChanged(newItem.selected)
            }
            if (oldItem is ChatHistoryListEntry.Row && newItem is ChatHistoryListEntry.Row &&
                oldItem.item == newItem.item && oldItem.selectMode != newItem.selectMode
            ) {
                return RowChange.SelectModeChanged
            }
            return null
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ROW = 1
    }
}
