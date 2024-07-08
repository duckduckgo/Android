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

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.mobile.android.databinding.RowTwoLineItemBinding
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.RowBookmarkTwoLineItemBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptySearchHintBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import kotlinx.coroutines.launch

sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EmptyHint(
        private val binding: ViewSavedSiteEmptyHintBinding,
        private val viewModel: BookmarksViewModel,
    ) : BookmarkScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteEmptyHintTitle.setText(R.string.bookmarksEmptyHint)
            binding.savedSiteEmptyImportButton.setOnClickListener {
                viewModel.launchBookmarkImport()
            }
        }
    }

    class EmptySearchHint(
        private val binding: ViewSavedSiteEmptySearchHintBinding,
        private val viewModel: BookmarksViewModel,
        lifecycleOwner: LifecycleOwner,
    ) : BookmarkScreenViewHolders(binding.root) {

        init {
            viewModel.viewState.observe(lifecycleOwner) {
                updateText(it.searchQuery)
            }
        }

        private fun updateText(query: String) {
            binding.savedSiteEmptyHint.text = binding.root.context.getString(R.string.noResultsFor, query)
        }
        fun bind() {
            viewModel.viewState.value?.let { updateText(it.searchQuery) }
        }
    }

    class BookmarksViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: RowBookmarkTwoLineItemBinding,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
    ) : BookmarkScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context
        private var isFavorite = false
        private var faviconLoaded = false
        private var bookmark: SavedSite.Bookmark? = null

        fun showDragHandle(show: Boolean, bookmark: SavedSite.Bookmark) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener {}
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                binding.root.setTrailingIconClickListener { anchor ->
                    showOverFlowMenu(anchor, bookmark)
                }
            }
        }

        fun update(bookmark: SavedSite.Bookmark) {
            val listItem = binding.root
            listItem.setBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorBackground))
            listItem.setLeadingIconContentDescription(
                context.getString(
                    R.string.bookmarkOverflowContentDescription,
                    bookmark.title,
                ),
            )
            listItem.setPrimaryText(bookmark.title)
            listItem.setSecondaryText(parseDisplayUrl(bookmark.url))

            if (this.bookmark?.url != bookmark.url) {
                loadFavicon(bookmark.url, listItem.leadingIcon())
                faviconLoaded = true
            }
            listItem.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
            listItem.setTrailingIconClickListener { anchor ->
                showOverFlowMenu(anchor, bookmark)
            }
            listItem.setClickListener {
                viewModel.onSelected(bookmark)
            }
            isFavorite = bookmark.isFavorite
            listItem.setFavoriteStarVisible(isFavorite)

            this.bookmark = bookmark
        }

        private fun loadFavicon(url: String, image: ImageView) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewMaybeFromRemoteWithPlaceholder(url = url, view = image)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }

        private fun showOverFlowMenu(
            anchor: View,
            bookmark: SavedSite.Bookmark,
        ) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_favorite_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { editBookmark(bookmark) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { deleteBookmark(bookmark) }
                onMenuItemClicked(view.findViewById(R.id.addRemoveFavorite)) {
                    addRemoveFavorite(bookmark)
                }
            }
            if (isFavorite) {
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).setPrimaryText(context.getString(R.string.removeFromFavorites))
            } else {
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).setPrimaryText(context.getString(R.string.addToFavoritesMenu))
            }
            popupMenu.show(binding.root, anchor)
        }

        private fun editBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onEditSavedSiteRequested(bookmark)
        }

        private fun deleteBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onDeleteSavedSiteRequested(bookmark)
        }

        private fun addRemoveFavorite(bookmark: SavedSite.Bookmark) {
            if (bookmark.isFavorite) {
                viewModel.removeFavorite(bookmark)
            } else {
                viewModel.addFavorite(bookmark)
            }
        }
    }

    class BookmarkFoldersViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: RowTwoLineItemBinding,
        private val viewModel: BookmarksViewModel,
    ) : BookmarkScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context

        fun showDragHandle(show: Boolean, bookmarkFolder: BookmarkFolder) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener {}
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                binding.root.setTrailingIconClickListener {
                    showOverFlowMenu(binding.root, bookmarkFolder)
                }
            }
        }

        fun update(bookmarkFolder: BookmarkFolder) {
            val listItem = binding.root
            listItem.setBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorBackground))

            listItem.setPrimaryText(bookmarkFolder.name)

            val totalItems = bookmarkFolder.numBookmarks + bookmarkFolder.numFolders
            if (totalItems == 0) {
                listItem.setSecondaryText(context.getString(R.string.bookmarkFolderEmpty))
            } else {
                listItem.setSecondaryText(context.resources.getQuantityString(R.plurals.bookmarkFolderItems, totalItems, totalItems))
            }
            listItem.setLeadingIconResource(R.drawable.ic_folder_24)

            listItem.showTrailingIcon()
            listItem.setTrailingIconClickListener {
                showOverFlowMenu(listItem, bookmarkFolder)
            }
            listItem.setOnClickListener {
                viewModel.onBookmarkFolderSelected(bookmarkFolder)
            }
        }

        private fun showOverFlowMenu(
            anchor: View,
            bookmarkFolder: BookmarkFolder,
        ) {
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
