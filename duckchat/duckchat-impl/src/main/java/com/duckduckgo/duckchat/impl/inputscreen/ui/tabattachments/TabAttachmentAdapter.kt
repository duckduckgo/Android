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

package com.duckduckgo.duckchat.impl.inputscreen.ui.tabattachments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.duckchat.impl.databinding.ItemTabAttachmentBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TabAttachmentAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val onTabClicked: (TabAttachmentItem) -> Unit,
) : ListAdapter<TabAttachmentItem, TabAttachmentAdapter.TabAttachmentViewHolder>(TabAttachmentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabAttachmentViewHolder {
        val binding = ItemTabAttachmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return TabAttachmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabAttachmentViewHolder, position: Int) {
        holder.bind(getItem(position), lifecycleOwner, faviconManager, onTabClicked)
    }

    class TabAttachmentViewHolder(
        private val binding: ItemTabAttachmentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var faviconJob: Job? = null

        fun bind(
            item: TabAttachmentItem,
            lifecycleOwner: LifecycleOwner,
            faviconManager: FaviconManager,
            onTabClicked: (TabAttachmentItem) -> Unit,
        ) {
            binding.tabAttachmentTitle.text = item.title
            faviconJob?.cancel()
            faviconJob = lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalWithPlaceholder(
                    tabId = item.tabId,
                    url = item.url,
                    view = binding.tabAttachmentIcon,
                )
            }
            binding.root.setOnClickListener {
                onTabClicked(item)
            }
        }
    }

    companion object {
        private val TabAttachmentDiffCallback = object : DiffUtil.ItemCallback<TabAttachmentItem>() {
            override fun areItemsTheSame(oldItem: TabAttachmentItem, newItem: TabAttachmentItem): Boolean {
                return oldItem.tabId == newItem.tabId
            }

            override fun areContentsTheSame(oldItem: TabAttachmentItem, newItem: TabAttachmentItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
