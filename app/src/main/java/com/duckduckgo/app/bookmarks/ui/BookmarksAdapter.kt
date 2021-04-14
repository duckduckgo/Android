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
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.baseHost
import kotlinx.android.synthetic.main.popup_window_bookmarks_menu.view.*
import kotlinx.android.synthetic.main.view_bookmark_entry.view.*
import kotlinx.android.synthetic.main.view_boomark_empty_hint.view.*
import kotlinx.android.synthetic.main.view_location_permissions_section_title.view.*
import kotlinx.coroutines.launch
import timber.log.Timber

class BookmarksAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : RecyclerView.Adapter<BookmarkScreenViewHolders>() {

    companion object {
        const val BOOKMARK_SECTION_TITLE_TYPE = 0
        const val EMPTY_STATE_TYPE = 1
        const val BOOKMARK_TYPE = 2

        const val BOOKMARK_SECTION_TITLE_SIZE = 1
        const val BOOKMARK_EMPTY_HINT_SIZE = 1
    }

    var bookmarkItems: List<SavedSite.Bookmark> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BOOKMARK_TYPE -> {
                val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
                return BookmarkScreenViewHolders.BookmarksViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
            }
            BOOKMARK_SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_section_title, parent, false)
                return BookmarkScreenViewHolders.SectionTitle(view)
            }
            EMPTY_STATE_TYPE -> {
                val view = inflater.inflate(R.layout.view_boomark_empty_hint, parent, false)
                BookmarkScreenViewHolders.EmptyHint(view)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemCount(): Int {
        return headerItemsSize() + listSize()
    }

    override fun onBindViewHolder(holder: BookmarkScreenViewHolders, position: Int) {
        when (holder) {
            is BookmarkScreenViewHolders.BookmarksViewHolder -> {
                holder.update(bookmarkItems[position - headerItemsSize()])
            }
            is BookmarkScreenViewHolders.SectionTitle -> {
                holder.bind()
            }
            is BookmarkScreenViewHolders.EmptyHint -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> {
                BOOKMARK_SECTION_TITLE_TYPE
            }
            bookmarkItems.isEmpty() -> {
                EMPTY_STATE_TYPE
            }
            else -> {
                BOOKMARK_TYPE
            }
        }
    }

    private fun headerItemsSize(): Int {
        return BOOKMARK_SECTION_TITLE_SIZE
    }

    private fun listSize() = if (bookmarkItems.isEmpty()) BOOKMARK_EMPTY_HINT_SIZE else bookmarkItems.size
}

sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class SectionTitle(itemView: View) : BookmarkScreenViewHolders(itemView) {
        fun bind() {
            itemView.locationPermissionsSectionTitle.setText(R.string.bookmarksSectionTitle)
        }
    }

    class EmptyHint(itemView: View) : BookmarkScreenViewHolders(itemView) {
        fun bind() {
            itemView.bookmarksEmptyHint.setText(R.string.bookmarksEmptyHint)
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
                faviconManager.loadToViewFromPersisted(url, itemView.favicon)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }

        private fun showOverFlowMenu(anchor: ImageView, bookmark: SavedSite.Bookmark) {
            val popupMenu = BookmarksPopupMenu(layoutInflater)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.editBookmark) { editBookmark(bookmark) }
                onMenuItemClicked(view.deleteBookmark) { deleteBookmark(bookmark) }
            }
            popupMenu.show(itemView, anchor)
        }

        private fun editBookmark(bookmark: SavedSite.Bookmark) {
            Timber.i("Editing bookmark ${bookmark.title}")
            viewModel.onEditBookmarkRequested(bookmark)
        }

        private fun deleteBookmark(bookmark: SavedSite.Bookmark) {
            Timber.i("Deleting bookmark ${bookmark.title}")
            viewModel.onDeleteRequested(bookmark)
        }
    }
}