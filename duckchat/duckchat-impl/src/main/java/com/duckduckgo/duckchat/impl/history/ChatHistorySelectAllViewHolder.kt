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

class ChatHistorySelectAllViewHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {

    private val icon: ImageView = itemView.findViewById(R.id.chatHistorySelectAllIcon)
    private val label: DaxTextView = itemView.findViewById(R.id.chatHistorySelectAllLabel)

    fun bind(allSelected: Boolean, onClick: () -> Unit) {
        if (allSelected) {
            icon.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_check_blue_round_24)
            label.setText(R.string.duck_ai_chat_history_unselect_all)
        } else {
            icon.setImageResource(R.drawable.ic_chat_history_unchecked_24)
            label.setText(R.string.duck_ai_chat_history_select_all)
        }
        itemView.setOnClickListener { onClick() }
    }

    companion object {
        fun create(parent: ViewGroup): ChatHistorySelectAllViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_chat_history_select_all, parent, false)
            return ChatHistorySelectAllViewHolder(view)
        }
    }
}
