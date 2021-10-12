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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewBookmarkFolderEntryBinding
import com.duckduckgo.app.browser.databinding.ViewSavedSiteSectionTitleBinding

class BookmarkFoldersAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val parentId: Long
) : ListAdapter<BookmarkFoldersAdapter.BookmarkFoldersItemTypes, BookmarkFolderScreenViewHolders>(BookmarkFoldersDiffCallback()) {

    companion object {
        const val BOOKMARK_FOLDERS_SECTION_TITLE_TYPE = 0
        const val BOOKMARK_FOLDER_TYPE = 1
    }

    interface BookmarkFoldersItemTypes
    object Header : BookmarkFoldersItemTypes
    data class BookmarkFolderItem(val bookmarkFolder: BookmarkFolder) : BookmarkFoldersItemTypes

    var bookmarkFolderItems: List<BookmarkFoldersItemTypes> = emptyList()
        set(value) {
            field = generateNewList(value)
            submitList(field)
        }

    private fun generateNewList(value: List<BookmarkFoldersItemTypes>): List<BookmarkFoldersItemTypes> {
        return if (parentId == 0L) {
            listOf(Header) + value
        } else {
            value
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkFolderScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BOOKMARK_FOLDER_TYPE -> {
                val binding = ViewBookmarkFolderEntryBinding.inflate(inflater, parent, false)
                BookmarkFolderScreenViewHolders.BookmarkFoldersViewHolder(layoutInflater, binding, viewModel)
            }
            BOOKMARK_FOLDERS_SECTION_TITLE_TYPE -> {
                val binding = ViewSavedSiteSectionTitleBinding.inflate(inflater, parent, false)
                BookmarkFolderScreenViewHolders.SectionTitle(binding)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemCount(): Int = bookmarkFolderItems.size

    override fun onBindViewHolder(holder: BookmarkFolderScreenViewHolders, position: Int) {
        when (holder) {
            is BookmarkFolderScreenViewHolders.BookmarkFoldersViewHolder -> {
                holder.update((bookmarkFolderItems[position] as BookmarkFolderItem).bookmarkFolder)
            }
            is BookmarkFolderScreenViewHolders.SectionTitle -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (bookmarkFolderItems[position]) {
            is Header -> BOOKMARK_FOLDERS_SECTION_TITLE_TYPE
            else -> BOOKMARK_FOLDER_TYPE
        }
    }
}

sealed class BookmarkFolderScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class SectionTitle(private val binding: ViewSavedSiteSectionTitleBinding) : BookmarkFolderScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteSectionTitle.setText(R.string.bookmarksSectionTitle)
        }
    }

    class BookmarkFoldersViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: ViewBookmarkFolderEntryBinding,
        private val viewModel: BookmarksViewModel
    ) : BookmarkFolderScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context

        fun update(bookmarkFolder: BookmarkFolder) {
            binding.overflowMenu.contentDescription = context.getString(
                R.string.bookmarkOverflowContentDescription,
                bookmarkFolder.name
            )

            binding.title.text = bookmarkFolder.name

            val totalItems = bookmarkFolder.numBookmarks + bookmarkFolder.numFolders

            if (totalItems == 0) {
                binding.subtitle.text = context.getString(R.string.bookmarkFolderEmpty)
            } else {
                binding.subtitle.text = context.resources.getQuantityString(R.plurals.bookmarkFolderItems, totalItems, totalItems)
            }

            binding.icon.setImageResource(R.drawable.ic_folder)

            binding.overflowMenu.setOnClickListener {
                showOverFlowMenu(binding.overflowMenu, bookmarkFolder)
            }

            binding.root.setOnClickListener {
                viewModel.onBookmarkFolderSelected(bookmarkFolder)
            }
        }

        private fun showOverFlowMenu(anchor: ImageView, bookmarkFolder: BookmarkFolder) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { editBookmarkFolder(bookmarkFolder) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { deleteBookmarkFolder(bookmarkFolder) }
            }
            popupMenu.show(binding.root, anchor)
        }

        private fun editBookmarkFolder(bookmarkFolder: BookmarkFolder) {
            viewModel.onEditBookmarkFolderRequested(bookmarkFolder)
        }

        private fun deleteBookmarkFolder(bookmarkFolder: BookmarkFolder) {
            viewModel.onDeleteBookmarkFolderRequested(bookmarkFolder)
        }
    }
}

class BookmarkFoldersDiffCallback : DiffUtil.ItemCallback<BookmarkFoldersAdapter.BookmarkFoldersItemTypes>() {
    override fun areItemsTheSame(oldItem: BookmarkFoldersAdapter.BookmarkFoldersItemTypes, newItem: BookmarkFoldersAdapter.BookmarkFoldersItemTypes): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: BookmarkFoldersAdapter.BookmarkFoldersItemTypes, newItem: BookmarkFoldersAdapter.BookmarkFoldersItemTypes): Boolean {
        return oldItem == newItem
    }
}
