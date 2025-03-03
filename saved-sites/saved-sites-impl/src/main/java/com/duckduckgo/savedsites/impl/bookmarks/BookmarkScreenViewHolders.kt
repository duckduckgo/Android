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
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.RowBookmarkTwoLineItemBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.saved.sites.impl.databinding.ViewSavedSiteEmptySearchHintBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import kotlinx.coroutines.launch

sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EmptyHint(
        private val binding: ViewSavedSiteEmptyHintBinding,
        private val viewModel: BookmarksViewModel,
    ) : BookmarkScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteEmptyHintTitle.setText(R.string.bookmarksEmptyHint)
            binding.savedSiteEmptyImportButton.setOnClickListener {
                viewModel.onImportBookmarksClicked()
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
        private val binding: RowBookmarkTwoLineItemBinding,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
        private val onBookmarkClick: (Bookmark) -> Unit,
        private val onBookmarkOverflowClick: (View, Bookmark) -> Unit,
        private val onLongClick: () -> Unit,
    ) : BookmarkScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context
        private var isFavorite = false
        private var faviconLoaded = false
        private var bookmark: SavedSite.Bookmark? = null

        fun showDragHandle(
            show: Boolean,
            bookmark: SavedSite.Bookmark,
        ) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener {}
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                binding.root.setTrailingIconClickListener { anchor ->
                    onBookmarkOverflowClick(anchor, bookmark)
                }
            }
        }

        fun update(
            bookmark: Bookmark,
        ) {
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
                onBookmarkOverflowClick(anchor, bookmark)
            }
            listItem.setClickListener {
                onBookmarkClick(bookmark)
            }

            listItem.setLongClickListener {
                onLongClick()
            }

            isFavorite = bookmark.isFavorite
            listItem.setFavoriteStarVisible(isFavorite)

            this.bookmark = bookmark
        }

        private fun loadFavicon(
            url: String,
            image: ImageView,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewMaybeFromRemoteWithPlaceholder(url = url, view = image)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }
    }

    class BookmarkFoldersViewHolder(
        private val binding: RowBookmarkTwoLineItemBinding,
        private val onBookmarkFolderClick: (View, BookmarkFolder) -> Unit,
        private val onBookmarkFolderOverflowClick: (View, BookmarkFolder) -> Unit,
        private val onLongClick: () -> Unit,
    ) : BookmarkScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context

        fun showDragHandle(
            show: Boolean,
            bookmarkFolder: BookmarkFolder,
        ) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener {}
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                binding.root.setTrailingIconClickListener { anchor ->
                    onBookmarkFolderOverflowClick(anchor, bookmarkFolder)
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

            listItem.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
            listItem.setTrailingIconClickListener { anchor ->
                onBookmarkFolderOverflowClick(anchor, bookmarkFolder)
            }

            listItem.setClickListener {
                onBookmarkFolderClick(listItem, bookmarkFolder)
            }

            listItem.setLongClickListener {
                onLongClick()
            }
        }
    }
}
