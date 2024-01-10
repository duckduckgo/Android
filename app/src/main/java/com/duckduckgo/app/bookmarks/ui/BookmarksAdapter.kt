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
import android.graphics.Canvas
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
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarkFoldersViewHolder
import com.duckduckgo.app.bookmarks.ui.BookmarkScreenViewHolders.BookmarksViewHolder
import com.duckduckgo.app.bookmarks.ui.BookmarksAdapter.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.ui.BookmarksAdapter.BookmarkItem
import com.duckduckgo.app.browser.R
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
import com.duckduckgo.savedsites.api.models.SavedSitesNames
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

sealed class BookmarkScreenViewHolders(itemView: View) : ViewHolder(itemView) {

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
        private val binding: RowTwoLineItemBinding,
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
            listItem.setPillVisible(isFavorite)

            this.bookmark = bookmark
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
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).label(context.getString(R.string.removeFromFavorites))
            } else {
                view.findViewById<PopupMenuItemView>(R.id.addRemoveFavorite).label(context.getString(R.string.addToFavoritesMenu))
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

class BookmarkItemTouchHelperCallback(
    private val adapter: BookmarksAdapter,
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return !adapter.isInSearchMode
    }
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
        target: ViewHolder,
    ): Boolean {
        adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            adapter.isReorderingModeEnabled = true
            updateDragHandle(viewHolder, true)
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            adapter.isReorderingModeEnabled = false
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        updateDragHandle(viewHolder, false)
        adapter.persistReorderedItems()
    }

    private fun updateDragHandle(viewHolder: ViewHolder?, showHandle: Boolean) {
        (viewHolder as? BookmarkScreenViewHolders)?.let {
            when (it) {
                is BookmarksViewHolder -> {
                    val bookmarkItem = adapter.bookmarkItems[it.bindingAdapterPosition] as BookmarkItem
                    it.showDragHandle(showHandle, bookmarkItem.bookmark)
                }
                is BookmarkFoldersViewHolder -> {
                    val folderItem = adapter.bookmarkItems[it.bindingAdapterPosition] as BookmarkFolderItem
                    it.showDragHandle(showHandle, folderItem.bookmarkFolder)
                }
                else -> {}
            }
        }
    }

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
        // Not handled
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        if (isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.elevation = 16f
            }
        } else {
            viewHolder.itemView.elevation = 0f
        }
    }
}
