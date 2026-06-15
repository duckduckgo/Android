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
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.duckchat.impl.R

class ChatHistorySectionHeaderViewHolder(
    private val sectionHeader: SectionHeaderListItem,
) : RecyclerView.ViewHolder(sectionHeader) {

    fun bind(@StringRes labelRes: Int) {
        sectionHeader.setText(labelRes)
    }

    companion object {
        fun create(parent: ViewGroup): ChatHistorySectionHeaderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_chat_history_section_header, parent, false) as SectionHeaderListItem
            return ChatHistorySectionHeaderViewHolder(view)
        }
    }
}
