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

package com.duckduckgo.app.bookmarks.ui.bookmarkfolders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.browser.databinding.ItemBookmarkFolderBinding
import com.duckduckgo.app.global.view.toPx

class BookmarkFolderStructureAdapter(
    viewWidth: Int
) : ListAdapter<Pair<Int, BookmarkFolder>, FolderViewHolder>(BookmarkFolderStructureDiffCallback()) {

    companion object {
        const val VERTICAL_PADDING_DP = 8
        const val PADDING_INCREMENT_DP = 16
        const val WIDTH_FACTOR = 0.5
    }

    private val verticalPaddingPx = VERTICAL_PADDING_DP.toPx()
    private val paddingIncrementPx = PADDING_INCREMENT_DP.toPx()

    private val maxWidth = viewWidth * WIDTH_FACTOR
    private val maxPadding = (maxWidth - (maxWidth % paddingIncrementPx)).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemBookmarkFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding, paddingIncrementPx, maxPadding, verticalPaddingPx)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class FolderViewHolder(
    private val binding: ItemBookmarkFolderBinding,
    private val paddingIncrement: Int,
    private val maxPadding: Int,
    private val verticalPadding: Int
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Pair<Int, BookmarkFolder>) {
        binding.name.text = item.second.name
        setPadding(item.first)
    }

    private fun setPadding(depth: Int) {
        var leftPadding = paddingIncrement + depth * paddingIncrement
        if (leftPadding > maxPadding) {
            leftPadding = maxPadding
        }
        binding.root.setPadding(leftPadding, verticalPadding, paddingIncrement, verticalPadding)
    }
}

class BookmarkFolderStructureDiffCallback : DiffUtil.ItemCallback<Pair<Int, BookmarkFolder>>() {
    override fun areItemsTheSame(oldItem: Pair<Int, BookmarkFolder>, newItem: Pair<Int, BookmarkFolder>): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Pair<Int, BookmarkFolder>, newItem: Pair<Int, BookmarkFolder>): Boolean {
        return oldItem == newItem
    }
}
