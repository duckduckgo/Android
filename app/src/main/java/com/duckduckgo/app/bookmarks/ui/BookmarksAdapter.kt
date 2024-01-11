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

package com.duckduckgo.app.bookmarks.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarkFoldersViewHolder
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarksViewHolder
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptySearchHintBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.mobile.android.databinding.RowTwoLineItemBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import java.util.Collections

class BookmarksAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
) : RecyclerView.Adapter<BookmarkScreenViewHolders>() {

    companion object {
        const val EMPTY_STATE_TYPE = 0
        const val EMPTY_SEARCH_STATE_TYPE = 1
        const val BOOKMARK_TYPE = 2
        const val BOOKMARK_FOLDER_TYPE = 3
    }

    val bookmarkItems = mutableListOf<BookmarksItemTypes>()
    var isInSearchMode = false
    var isReorderingModeEnabled = false

    interface BookmarksItemTypes
    object EmptyHint : BookmarksItemTypes
    object EmptySearchHint : BookmarksItemTypes
    data class BookmarkItem(val bookmark: SavedSite.Bookmark) : BookmarksItemTypes
    data class BookmarkFolderItem(val bookmarkFolder: BookmarkFolder) : BookmarksItemTypes

    fun setItems(
        bookmarkItems: List<BookmarksItemTypes>,
        showEmptyHint: Boolean,
        showEmptySearchHint: Boolean,
    ) {
        val generatedList = generateNewList(bookmarkItems, showEmptyHint, showEmptySearchHint)
        val diffCallback = DiffCallback(old = this.bookmarkItems, new = generatedList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.bookmarkItems.clear().also { this.bookmarkItems.addAll(generatedList) }
        diffResult.dispatchUpdatesTo(this)
    }

    private fun generateNewList(
        value: List<BookmarksItemTypes>,
        showEmptyHint: Boolean,
        showEmptySearchHint: Boolean,
    ): List<BookmarksItemTypes> {
        if (showEmptySearchHint) {
            return value.ifEmpty { listOf(EmptySearchHint) }
        }
        if (!showEmptyHint) {
            return value
        }
        return value.ifEmpty { listOf(EmptyHint) }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BookmarkScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BOOKMARK_TYPE -> {
                val binding = RowTwoLineItemBinding.inflate(inflater, parent, false)
                return BookmarksViewHolder(
                    layoutInflater,
                    binding,
                    viewModel,
                    lifecycleOwner,
                    faviconManager,
                )
            }
            BOOKMARK_FOLDER_TYPE -> {
                val binding = RowTwoLineItemBinding.inflate(inflater, parent, false)
                return BookmarkFoldersViewHolder(
                    layoutInflater,
                    binding,
                    viewModel,
                )
            }
            EMPTY_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptyHintBinding.inflate(inflater, parent, false)
                BookmarkScreenViewHolders.EmptyHint(binding, viewModel)
            }
            EMPTY_SEARCH_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptySearchHintBinding.inflate(inflater, parent, false)
                BookmarkScreenViewHolders.EmptySearchHint(binding, viewModel, lifecycleOwner)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun onBindViewHolder(
        holder: BookmarkScreenViewHolders,
        position: Int,
    ) {
        when (holder) {
            is BookmarksViewHolder -> {
                val bookmark = (this.bookmarkItems[position] as BookmarkItem).bookmark
                holder.update(bookmark)
                holder.showDragHandle(isReorderingModeEnabled, bookmark)
            }
            is BookmarkFoldersViewHolder -> {
                val bookmarkFolder = (this.bookmarkItems[position] as BookmarkFolderItem).bookmarkFolder
                holder.update(bookmarkFolder)
                holder.showDragHandle(isReorderingModeEnabled, bookmarkFolder)
            }
            is BookmarkScreenViewHolders.EmptyHint -> {
                holder.bind()
            }
            is BookmarkScreenViewHolders.EmptySearchHint -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (this.bookmarkItems[position]) {
            is EmptyHint -> EMPTY_STATE_TYPE
            is EmptySearchHint -> EMPTY_SEARCH_STATE_TYPE
            is BookmarkFolderItem -> BOOKMARK_FOLDER_TYPE
            else -> BOOKMARK_TYPE
        }
    }

    override fun getItemCount(): Int {
        return this.bookmarkItems.size
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(bookmarkItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun persistReorderedItems() {
        var parentId = SavedSitesNames.BOOKMARKS_ROOT
        val reorderedBookmarks = bookmarkItems.mapNotNull { item ->
            when (item) {
                is BookmarkItem -> {
                    parentId = item.bookmark.parentId
                    item.bookmark.id
                }
                is BookmarkFolderItem -> {
                    parentId = item.bookmarkFolder.parentId
                    item.bookmarkFolder.id
                }
                else -> ""
            }
        }
        viewModel.updateBookmarks(reorderedBookmarks, parentId)
    }

    class DiffCallback(
        private val old: List<BookmarksItemTypes>,
        private val new: List<BookmarksItemTypes>,
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = old[oldItemPosition]
            val newItem = new[newItemPosition]
            return if (oldItem is BookmarkItem && newItem is BookmarkItem) {
                oldItem.bookmark.id == newItem.bookmark.id
            } else if (oldItem is BookmarkFolderItem && newItem is BookmarkFolderItem) {
                oldItem.bookmarkFolder.id == newItem.bookmarkFolder.id
            } else {
                old[oldItemPosition] == new[newItemPosition]
            }
        }

        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }
}
