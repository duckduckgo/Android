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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel
import com.duckduckgo.app.bookmarks.ui.SavedSitePopupMenu
import com.duckduckgo.app.browser.R
import kotlinx.android.synthetic.main.popup_window_saved_site_menu.view.*
import kotlinx.android.synthetic.main.view_saved_site_entry.view.*
import kotlinx.android.synthetic.main.view_saved_site_section_title.view.*

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
                val view = inflater.inflate(R.layout.view_saved_site_entry, parent, false)
                BookmarkFolderScreenViewHolders.BookmarkFoldersViewHolder(layoutInflater, view, viewModel)
            }
            BOOKMARK_FOLDERS_SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_saved_site_section_title, parent, false)
                BookmarkFolderScreenViewHolders.SectionTitle(view)
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

    class SectionTitle(itemView: View) : BookmarkFolderScreenViewHolders(itemView) {
        fun bind() {
            itemView.savedSiteSectionTitle.setText(R.string.bookmarksSectionTitle)
        }
    }

    class BookmarkFoldersViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: BookmarksViewModel
    ) : BookmarkFolderScreenViewHolders(itemView) {

        fun update(bookmarkFolder: BookmarkFolder) {
            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.bookmarkOverflowContentDescription,
                bookmarkFolder.name
            )

            itemView.title.text = bookmarkFolder.name

            val totalItems = bookmarkFolder.numBookmarks + bookmarkFolder.numFolders
            itemView.subtitle.text = itemView.context.resources.getQuantityString(R.plurals.bookmarkFolderItems, totalItems, totalItems)

            itemView.icon.visibility = View.VISIBLE
            itemView.favicon.visibility = View.GONE
            itemView.icon.setImageResource(R.drawable.ic_folder)

            itemView.overflowMenu.setOnClickListener {
                showOverFlowMenu(itemView.overflowMenu, bookmarkFolder)
            }

            itemView.setOnClickListener {
                viewModel.onBookmarkFolderSelected(bookmarkFolder)
            }
        }

        private fun showOverFlowMenu(anchor: ImageView, bookmarkFolder: BookmarkFolder) {
            val popupMenu = SavedSitePopupMenu(layoutInflater)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.editSavedSite) { editBookmarkFolder(bookmarkFolder) }
                onMenuItemClicked(view.deleteSavedSite) { deleteBookmarkFolder(bookmarkFolder) }
            }
            popupMenu.show(itemView, anchor)
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
