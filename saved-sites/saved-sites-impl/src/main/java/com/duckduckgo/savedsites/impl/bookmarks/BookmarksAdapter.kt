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

package com.duckduckgo.savedsites.impl.bookmarks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.saved.sites.impl.databinding.RowBookmarkTwoLineItemBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptySearchHintBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.BookmarkFoldersViewHolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.BookmarksViewHolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.EmptyHint
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.EmptySearchHint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class BookmarksAdapter(
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val dispatcherProvider: DispatcherProvider,
    private val faviconManager: FaviconManager,
    private val onBookmarkClick: (Bookmark) -> Unit,
    private val onBookmarkOverflowClick: (View, Bookmark) -> Unit,
    private val onLongClick: () -> Unit,
    private val onBookmarkFolderClick: (View, BookmarkFolder) -> Unit,
    private val onBookmarkFolderOverflowClick: (View, BookmarkFolder) -> Unit,
) : RecyclerView.Adapter<BookmarkScreenViewHolders>() {

    companion object {
        const val EMPTY_STATE_TYPE = 0
        const val EMPTY_SEARCH_STATE_TYPE = 1
        const val BOOKMARK_TYPE = 2
        const val BOOKMARK_FOLDER_TYPE = 3
    }

    val bookmarkItems = mutableListOf<BookmarksItemTypes>()
    var isInSearchMode = false
    var isReordering = false
    var isReorderingEnabled = false

    private val bookmarkItemsUpdateJob = ConflatedJob()

    sealed interface BookmarksItemTypes
    data object EmptyHint : BookmarksItemTypes
    data object EmptySearchHint : BookmarksItemTypes
    data class BookmarkItem(val bookmark: SavedSite.Bookmark) : BookmarksItemTypes
    data class BookmarkFolderItem(val bookmarkFolder: BookmarkFolder) : BookmarksItemTypes

    fun setItems(
        newBookmarkItems: List<BookmarksItemTypes>,
        showEmptyHint: Boolean,
        showEmptySearchHint: Boolean,
        detectMoves: Boolean,
    ) {
        bookmarkItemsUpdateJob += lifecycleOwner.lifecycleScope.launch(dispatcherProvider.main()) {
            val oldBookmarkItemsLocalRef = bookmarkItems.toList()
            val newBookmarkItemsLocalRef = newBookmarkItems.toList()
            val (generatedList, diffResult) = withContext(dispatcherProvider.computation()) {
                val generatedList = generateNewList(newBookmarkItemsLocalRef, showEmptyHint, showEmptySearchHint)
                val diffCallback = DiffCallback(old = oldBookmarkItemsLocalRef, new = generatedList)
                val diffResult = DiffUtil.calculateDiff(diffCallback, detectMoves)
                generatedList to diffResult
            }
            bookmarkItems.clear().also { bookmarkItems.addAll(generatedList) }
            diffResult.dispatchUpdatesTo(this@BookmarksAdapter)
        }
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
                val binding = RowBookmarkTwoLineItemBinding.inflate(inflater, parent, false)
                return BookmarksViewHolder(
                    binding,
                    lifecycleOwner,
                    faviconManager,
                    onBookmarkClick,
                    onBookmarkOverflowClick,
                    onLongClick,
                )
            }
            BOOKMARK_FOLDER_TYPE -> {
                val binding = RowBookmarkTwoLineItemBinding.inflate(inflater, parent, false)
                return BookmarkFoldersViewHolder(
                    binding,
                    onBookmarkFolderClick,
                    onBookmarkFolderOverflowClick,
                    onLongClick,
                )
            }
            EMPTY_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptyHintBinding.inflate(inflater, parent, false)
                EmptyHint(binding, viewModel)
            }
            EMPTY_SEARCH_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptySearchHintBinding.inflate(inflater, parent, false)
                EmptySearchHint(binding, viewModel, lifecycleOwner)
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
                holder.showDragHandle(isReordering, bookmark)
            }
            is BookmarkFoldersViewHolder -> {
                val bookmarkFolder = (this.bookmarkItems[position] as BookmarkFolderItem).bookmarkFolder
                holder.update(bookmarkFolder)
                holder.showDragHandle(isReordering, bookmarkFolder)
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
