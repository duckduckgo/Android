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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.baseHost
import kotlinx.android.synthetic.main.popup_window_saved_site_menu.view.*
import kotlinx.android.synthetic.main.view_saved_site_entry.view.*
import kotlinx.android.synthetic.main.view_saved_site_empty_hint.view.*
import kotlinx.coroutines.launch

class BookmarksAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : ListAdapter<BookmarksAdapter.BookmarksItemTypes, BookmarkScreenViewHolders>(BookmarksDiffCallback()) {

    companion object {
        const val EMPTY_STATE_TYPE = 0
        const val BOOKMARK_TYPE = 1
    }

    interface BookmarksItemTypes
    object EmptyHint : BookmarksItemTypes
    data class BookmarkItem(val bookmark: SavedSite.Bookmark) : BookmarksItemTypes

    var bookmarkItems: List<BookmarksItemTypes> = emptyList()
        set(value) {
            field = generateNewList(value)
            submitList(field)
        }

    private fun generateNewList(value: List<BookmarksItemTypes>): List<BookmarksItemTypes> {
        return if (value.isEmpty()) listOf(EmptyHint) else value
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BOOKMARK_TYPE -> {
                val view = inflater.inflate(R.layout.view_saved_site_entry, parent, false)
                return BookmarkScreenViewHolders.BookmarksViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
            }
            EMPTY_STATE_TYPE -> {
                val view = inflater.inflate(R.layout.view_saved_site_empty_hint, parent, false)
                BookmarkScreenViewHolders.EmptyHint(view)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemCount(): Int = bookmarkItems.size

    override fun onBindViewHolder(holder: BookmarkScreenViewHolders, position: Int) {
        when (holder) {
            is BookmarkScreenViewHolders.BookmarksViewHolder -> {
                holder.update((bookmarkItems[position] as BookmarkItem).bookmark)
            }
            is BookmarkScreenViewHolders.EmptyHint -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (bookmarkItems[position]) {
            is EmptyHint -> EMPTY_STATE_TYPE
            else -> BOOKMARK_TYPE

        }
    }
}

sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EmptyHint(itemView: View) : BookmarkScreenViewHolders(itemView) {
        fun bind() {
            itemView.savedSiteEmptyHint.setText(R.string.bookmarksEmptyHint)
        }
    }

    class BookmarksViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : BookmarkScreenViewHolders(itemView) {

        fun update(bookmark: SavedSite.Bookmark) {
            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.bookmarkOverflowContentDescription,
                bookmark.title
            )

            itemView.title.text = bookmark.title
            itemView.url.text = parseDisplayUrl(bookmark.url)
            loadFavicon(bookmark.url)

            itemView.overflowMenu.setOnClickListener {
                showOverFlowMenu(itemView.overflowMenu, bookmark)
            }

            itemView.setOnClickListener {
                viewModel.onSelected(bookmark)
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(url = url, view = itemView.favicon)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }

        private fun showOverFlowMenu(anchor: ImageView, bookmark: SavedSite.Bookmark) {
            val popupMenu = SavedSitePopupMenu(layoutInflater)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.editSavedSite) { editBookmark(bookmark) }
                onMenuItemClicked(view.deleteSavedSite) { deleteBookmark(bookmark) }
            }
            popupMenu.show(itemView, anchor)
        }

        private fun editBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onEditSavedSiteRequested(bookmark)
        }

        private fun deleteBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onDeleteSavedSiteRequested(bookmark)
        }
    }
}
