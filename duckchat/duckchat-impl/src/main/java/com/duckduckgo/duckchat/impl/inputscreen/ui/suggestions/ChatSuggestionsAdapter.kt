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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ItemChatSuggestionBinding

class ChatSuggestionsAdapter(
    private val onChatClicked: (ChatSuggestion) -> Unit,
) : ListAdapter<ChatSuggestion, ChatSuggestionsAdapter.ChatSuggestionViewHolder>(ChatSuggestionsDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatSuggestionViewHolder {
        val binding = ItemChatSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ChatSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatSuggestionViewHolder, position: Int) {
        holder.bind(getItem(position), onChatClicked)
    }

    class ChatSuggestionViewHolder(
        private val binding: ItemChatSuggestionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: ChatSuggestion, onChatClicked: (ChatSuggestion) -> Unit) {
            binding.chatSuggestionTitle.text = suggestion.title

            val iconRes = if (suggestion.pinned) {
                R.drawable.ic_pin_24
            } else {
                R.drawable.ic_chat_24
            }
            binding.chatSuggestionIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onChatClicked(suggestion)
            }
        }
    }

    companion object {
        private val ChatSuggestionsDiffCallback = object : DiffUtil.ItemCallback<ChatSuggestion>() {
            override fun areItemsTheSame(oldItem: ChatSuggestion, newItem: ChatSuggestion): Boolean {
                return oldItem.chatId == newItem.chatId
            }

            override fun areContentsTheSame(oldItem: ChatSuggestion, newItem: ChatSuggestion): Boolean {
                return oldItem == newItem
            }
        }
    }
}
