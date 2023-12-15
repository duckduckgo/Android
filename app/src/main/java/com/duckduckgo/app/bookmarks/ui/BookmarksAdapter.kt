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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarkFoldersViewHolder
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarksViewHolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.string
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptySearchHintBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.mobile.android.databinding.RowTwoLineItemBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import java.util.Collections
import kotlinx.coroutines.launch

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
                holder.update((this.bookmarkItems[position] as BookmarkItem).bookmark)
                holder.showDragHandle(isReorderingModeEnabled, (this.bookmarkItems[position] as BookmarkItem).bookmark)
            }
            is BookmarkFoldersViewHolder -> {
                holder.update((this.bookmarkItems[position] as BookmarkFolderItem).bookmarkFolder)
                holder.showDragHandle(isReorderingModeEnabled, (this.bookmarkItems[position] as BookmarkFolderItem).bookmarkFolder)
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

    fun enterReorderingMode() {
        isReorderingModeEnabled = true
    }

    fun exitReorderingMode() {
        isReorderingModeEnabled = false
    }

    fun persistReorderedItems() {
        val reorderedBookmarks = bookmarkItems.mapNotNull { item ->
            when (item) {
                is BookmarkItem -> item.bookmark as Any
                is BookmarkFolderItem -> item.bookmarkFolder as Any
                else -> null
            }
        }
        viewModel.updateBookmarks(reorderedBookmarks)
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

sealed class BookmarkScreenViewHolders(itemView: View, viewModel: BookmarksViewModel) : RecyclerView.ViewHolder(itemView) {

    class EmptyHint(
        private val binding: ViewSavedSiteEmptyHintBinding,
        private val viewModel: BookmarksViewModel,
    ) : BookmarkScreenViewHolders(binding.root, viewModel) {
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
    ) : BookmarkScreenViewHolders(binding.root, viewModel) {

        init {
            viewModel.viewState.observe(lifecycleOwner) {
                updateText(it.searchQuery)
            }
        }

        private fun updateText(query: String) {
            binding.savedSiteEmptyHint.text = binding.root.context.getString(string.noResultsFor, query)
        }
        fun bind() {
            viewModel.viewState.value?.let { updateText(it.searchQuery) }
        }
    }

    class BookmarksViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: RowTwoLineItemBinding,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
    ) : BookmarkScreenViewHolders(binding.root, viewModel) {

        private val context: Context = binding.root.context
        private var isFavorite = false
        private var faviconLoaded = false
        private var currentBookmarkUrl = ""

        fun showDragHandle(show: Boolean, bookmark: SavedSite.Bookmark) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener(null)
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                setTrailingIconClickListener(bookmark)
            }
        }

        private fun setTrailingIconClickListener(bookmark: SavedSite.Bookmark) {
            binding.root.setTrailingIconClickListener { anchor ->
                showOverFlowMenu(anchor, bookmark)
            }
        }

        fun update(bookmark: SavedSite.Bookmark) {
            val twoListItem = binding.root

            twoListItem.setBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface))

            twoListItem.setLeadingIconContentDescription(
                context.getString(
                    R.string.bookmarkOverflowContentDescription,
                    bookmark.title,
                ),
            )
            twoListItem.setPrimaryText(bookmark.title)
            twoListItem.setSecondaryText(parseDisplayUrl(bookmark.url))

            if (!faviconLoaded || currentBookmarkUrl != bookmark.url) {
                loadFavicon(bookmark.url, twoListItem.leadingIcon())
                faviconLoaded = true
                currentBookmarkUrl = bookmark.url
            }

            twoListItem.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
            twoListItem.setTrailingIconClickListener { anchor ->
                showOverFlowMenu(anchor, bookmark)
            }

            twoListItem.setClickListener {
                (twoListItem.context as? BookmarksActivity)?.exitReorderingMode()
                viewModel.onSelected(bookmark)
            }

            isFavorite = bookmark.isFavorite
            twoListItem.setPillVisible(isFavorite)
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
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).label(context.getString(string.removeFromFavorites))
            } else {
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).label(context.getString(string.addToFavoritesMenu))
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
    ) : BookmarkScreenViewHolders(binding.root, viewModel) {

        private val context: Context = binding.root.context

        fun showDragHandle(show: Boolean, bookmarkFolder: BookmarkFolder) {
            if (show) {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24)
                binding.root.setTrailingIconClickListener(null)
            } else {
                binding.root.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
                setTrailingIconClickListener(bookmarkFolder)
            }
        }

        private fun setTrailingIconClickListener(bookmarkFolder: BookmarkFolder) {
            binding.root.setTrailingIconClickListener {
                showOverFlowMenu(binding.root, bookmarkFolder)
            }
        }

        fun update(bookmarkFolder: BookmarkFolder) {
            val listItem = binding.root

            listItem.setBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface))

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
                (listItem.context as? BookmarksActivity)?.exitReorderingMode()
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

class BookmarkItemTouchHelperCallback(
    private val adapter: BookmarksAdapter,
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return !adapter.isInSearchMode
    }
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSelectedChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            (viewHolder?.itemView?.context as? BookmarksActivity)?.enterReorderingMode()
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.persistReorderedItems()
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not handled
    }
}
