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

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.databinding.RowTwoLineItemBinding
import com.duckduckgo.savedsites.api.models.SavedSite
import kotlinx.coroutines.launch

class BookmarksAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val dispatchers: DispatcherProvider,
) : RecyclerView.Adapter<BookmarkScreenViewHolders>() {

    companion object {
        const val EMPTY_STATE_TYPE = 0
        const val BOOKMARK_TYPE = 1
    }

    private val bookmarkItems = mutableListOf<BookmarksItemTypes>()

    interface BookmarksItemTypes
    object EmptyHint : BookmarksItemTypes
    data class BookmarkItem(val bookmark: SavedSite.Bookmark) : BookmarksItemTypes

    fun setItems(
        bookmarkItems: List<BookmarkItem>,
        showEmptyHint: Boolean,
    ) {
        val generatedList = generateNewList(bookmarkItems, showEmptyHint)
        val diffCallback = DiffCallback(old = this.bookmarkItems, new = generatedList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.bookmarkItems.clear().also { this.bookmarkItems.addAll(generatedList) }
        diffResult.dispatchUpdatesTo(this)
    }

    private fun generateNewList(
        value: List<BookmarksItemTypes>,
        showEmptyHint: Boolean,
    ): List<BookmarksItemTypes> {
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
                return BookmarkScreenViewHolders.BookmarksViewHolder(
                    layoutInflater,
                    binding,
                    viewModel,
                    lifecycleOwner,
                    faviconManager,
                )
            }
            EMPTY_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptyHintBinding.inflate(inflater, parent, false)
                BookmarkScreenViewHolders.EmptyHint(binding)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun onBindViewHolder(
        holder: BookmarkScreenViewHolders,
        position: Int,
    ) {
        when (holder) {
            is BookmarkScreenViewHolders.BookmarksViewHolder -> {
                holder.update((this.bookmarkItems[position] as BookmarkItem).bookmark)
            }
            is BookmarkScreenViewHolders.EmptyHint -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (this.bookmarkItems[position]) {
            is EmptyHint -> EMPTY_STATE_TYPE
            else -> BOOKMARK_TYPE
        }
    }

    override fun getItemCount(): Int {
        return this.bookmarkItems.size
    }

    class DiffCallback(
        private val old: List<BookmarksItemTypes>,
        private val new: List<BookmarksItemTypes>,
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
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

sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EmptyHint(private val binding: ViewSavedSiteEmptyHintBinding) : BookmarkScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteEmptyHint.setText(R.string.bookmarksEmptyHint)
        }
    }

    class BookmarksViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: RowTwoLineItemBinding,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
    ) : BookmarkScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context

        fun update(bookmark: SavedSite.Bookmark) {
            val twoListItem = binding.root

            twoListItem.setLeadingIconContentDescription(
                context.getString(
                    R.string.bookmarkOverflowContentDescription,
                    bookmark.title,
                ),
            )
            twoListItem.setPrimaryText(bookmark.title)
            twoListItem.setSecondaryText(parseDisplayUrl(bookmark.url))

            loadFavicon(bookmark.url, twoListItem.leadingIcon())

            twoListItem.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
            twoListItem.setTrailingIconClickListener { anchor ->
                showOverFlowMenu(anchor, bookmark)
            }

            twoListItem.setClickListener {
                viewModel.onSelected(bookmark)
            }
        }

        private fun loadFavicon(url: String, image: ImageView) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalWithPlaceholder(url = url, view = image)
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
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { editBookmark(bookmark) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { deleteBookmark(bookmark) }
            }
            popupMenu.show(binding.root, anchor)
        }

        private fun editBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onEditSavedSiteRequested(bookmark)
        }

        private fun deleteBookmark(bookmark: SavedSite.Bookmark) {
            viewModel.onDeleteSavedSiteRequested(bookmark)
        }
    }
}
