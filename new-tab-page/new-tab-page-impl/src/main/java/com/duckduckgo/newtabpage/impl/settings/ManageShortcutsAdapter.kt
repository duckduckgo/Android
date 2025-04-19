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

package com.duckduckgo.newtabpage.impl.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.impl.R
import com.duckduckgo.newtabpage.impl.databinding.ViewNewTabSettingManageShortcutItemBinding
import com.duckduckgo.newtabpage.impl.settings.ManageShortcutsAdapter.ShortcutViewHolder

class ManageShortcutsAdapter(
    private val onShortcutSelected: (ManageShortcutItem) -> Unit,
) : ListAdapter<ManageShortcutItem, ShortcutViewHolder>(NewTabSectionsDiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ShortcutViewHolder {
        return ShortcutViewHolder(
            ViewNewTabSettingManageShortcutItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onShortcutSelected,
        )
    }

    override fun onBindViewHolder(
        holder: ShortcutViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class ShortcutViewHolder(
        private val binding: ViewNewTabSettingManageShortcutItemBinding,
        private val onShortcutSelected: (ManageShortcutItem) -> Unit,
    ) : ViewHolder(binding.root) {

        fun bind(
            item: ManageShortcutItem,
        ) {
            with(binding.shortcutItem) {
                setPrimaryText(item.plugin.getShortcut().titleResource())
                setLeadingIconDrawable(item.plugin.getShortcut().iconResource())
                setClickListener {
                    onShortcutSelected(item)
                }
            }
            if (item.selected) {
                binding.shortcutSelected.setImageResource(R.drawable.ic_shortcut_selected)
                binding.shortcutSelected.setBackgroundResource(R.drawable.background_shortcut_selected)
            } else {
                binding.shortcutSelected.setImageResource(R.drawable.ic_shortcut_unselected)
                binding.shortcutSelected.setBackgroundResource(R.drawable.background_shortcut_unselected)
            }
        }
    }
}

data class ManageShortcutItem(val plugin: NewTabPageShortcutPlugin, val selected: Boolean)
private class NewTabSectionsDiffCallback : DiffUtil.ItemCallback<ManageShortcutItem>() {
    override fun areItemsTheSame(
        oldItem: ManageShortcutItem,
        newItem: ManageShortcutItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: ManageShortcutItem,
        newItem: ManageShortcutItem,
    ): Boolean {
        return oldItem == newItem
    }
}
