/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.view.listitem.DaxGridItem.GridItemType.Placeholder
import com.duckduckgo.common.ui.view.listitem.DaxGridItem.GridItemType.Shortcut
import com.duckduckgo.mobile.android.databinding.RowNewTabGridItemBinding
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabSectionsItem.PlaceholderItem
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabSectionsItem.ShortcutItem

class ShortcutsAdapter(
    private val onShortcutSelected: (NewTabShortcut) -> Unit,
) : ListAdapter<NewTabSectionsItem, ViewHolder>(NewTabSectionsDiffCallback()) {

    var expanded: Boolean = false

    companion object {
        private const val PLACEHOLDER_VIEW_TYPE = 0
        private const val SHORTCUT_VIEW_TYPE = 1

        const val SHORTCUT_ITEM_MAX_SIZE_DP = 90
        const val SHORTCUT_GRID_MAX_COLUMNS = 6
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PlaceholderItem -> PLACEHOLDER_VIEW_TYPE
            is ShortcutItem -> SHORTCUT_VIEW_TYPE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            PLACEHOLDER_VIEW_TYPE -> PlaceholderViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )

            SHORTCUT_VIEW_TYPE -> ShortcutViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onShortcutSelected,
            )

            else -> ShortcutViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onShortcutSelected,
            )
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is PlaceholderViewHolder -> holder.bind()
            is ShortcutViewHolder -> holder.bind(getItem(position) as ShortcutItem)
        }
    }

    private class PlaceholderViewHolder(private val binding: RowNewTabGridItemBinding) : ViewHolder(binding.root) {
        fun bind() {
            binding.root.setItemType(Placeholder)
        }
    }

    private class ShortcutViewHolder(
        private val binding: RowNewTabGridItemBinding,
        private val onShortcutSelected: (NewTabShortcut) -> Unit,
    ) : ViewHolder(binding.root) {
        fun bind(
            item: ShortcutItem,
        ) {
            with(binding.root) {
                setItemType(Shortcut)
                setPrimaryText(item.shortcut.titleResource)
                setLeadingIconDrawable(item.shortcut.iconResource)
                setClickListener {
                    onShortcutSelected(item.shortcut)
                }
            }
        }
    }
}

sealed class NewTabSectionsItem {
    data object PlaceholderItem : NewTabSectionsItem()
    data class ShortcutItem(val shortcut: NewTabShortcut) : NewTabSectionsItem()
}

private class NewTabSectionsDiffCallback : DiffUtil.ItemCallback<NewTabSectionsItem>() {
    override fun areItemsTheSame(
        oldItem: NewTabSectionsItem,
        newItem: NewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: NewTabSectionsItem,
        newItem: NewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }
}
