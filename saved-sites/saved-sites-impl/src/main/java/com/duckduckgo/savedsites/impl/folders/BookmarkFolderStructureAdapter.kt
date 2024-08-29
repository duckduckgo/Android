/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.saved.sites.impl.databinding.ItemBookmarkFolderBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem

class BookmarkFolderStructureAdapter(
    private val viewModel: BookmarkFoldersViewModel,
    viewWidth: Int,
) : ListAdapter<BookmarkFolderItem, FolderViewHolder>(BookmarkFolderStructureDiffCallback()) {

    companion object {
        const val PADDING_INCREMENT_DP = 16
        const val WIDTH_FACTOR = 0.5
    }

    private val paddingIncrementPx = PADDING_INCREMENT_DP.toPx()

    private val maxWidth = viewWidth * WIDTH_FACTOR
    private val maxPadding = (maxWidth - (maxWidth % paddingIncrementPx)).toInt()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FolderViewHolder {
        val binding = ItemBookmarkFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding, viewModel, paddingIncrementPx, maxPadding)
    }

    override fun onBindViewHolder(
        holder: FolderViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}

class FolderViewHolder(
    private val binding: ItemBookmarkFolderBinding,
    private val viewModel: BookmarkFoldersViewModel,
    private val paddingIncrement: Int,
    private val maxPadding: Int,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: BookmarkFolderItem) {
        binding.bookmarkFolderItem.setPrimaryText(item.bookmarkFolder.name)
        setPadding(item.depth)

        binding.selectedFolderContainer.visibility = if (item.isSelected) View.VISIBLE else View.GONE

        itemView.setOnClickListener {
            viewModel.onItemSelected(item.bookmarkFolder)
        }
    }

    private fun setPadding(depth: Int) {
        var leftPadding = paddingIncrement + depth * paddingIncrement
        if (leftPadding > maxPadding) {
            leftPadding = maxPadding
        }
        binding.root.setPadding(leftPadding, 0, paddingIncrement, 0)
    }
}

class BookmarkFolderStructureDiffCallback : DiffUtil.ItemCallback<BookmarkFolderItem>() {
    override fun areItemsTheSame(
        oldItem: BookmarkFolderItem,
        newItem: BookmarkFolderItem,
    ): Boolean {
        return oldItem.bookmarkFolder.id == newItem.bookmarkFolder.id
    }

    override fun areContentsTheSame(
        oldItem: BookmarkFolderItem,
        newItem: BookmarkFolderItem,
    ): Boolean {
        return oldItem == newItem
    }
}
