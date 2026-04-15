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
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.duckchat.impl.databinding.ItemChatSearchSuggestionBinding

class ChatSearchSuggestionAdapter(
    private val onSearchClicked: (String) -> Unit,
) : RecyclerView.Adapter<ChatSearchSuggestionAdapter.ViewHolder>() {

    private var query: String = ""
    private var visible: Boolean = false

    fun update(query: String, visible: Boolean) {
        val wasVisible = this.visible
        val oldQuery = this.query
        this.query = query
        this.visible = visible
        when {
            wasVisible && !visible -> notifyItemRemoved(0)
            !wasVisible && visible -> notifyItemInserted(0)
            wasVisible && visible && oldQuery != query -> notifyItemChanged(0)
        }
    }

    override fun getItemCount(): Int = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatSearchSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(query, onSearchClicked)
    }

    class ViewHolder(
        private val binding: ItemChatSearchSuggestionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(query: String, onSearchClicked: (String) -> Unit) {
            binding.queryText.text = query
            binding.root.setOnClickListener { onSearchClicked(query) }
        }
    }
}
