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

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.BookmarkFoldersViewHolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarkScreenViewHolders.BookmarksViewHolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkFolderItem
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkItem

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

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            adapter.isReordering = true
            updateDragHandle(viewHolder, true)
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            adapter.isReordering = false
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        updateDragHandle(viewHolder, false)
        adapter.persistReorderedItems()
    }

    private fun updateDragHandle(viewHolder: RecyclerView.ViewHolder?, showHandle: Boolean) {
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

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not handled
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        if (isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.elevation = SHADOW_ELEVATION
            }
        } else {
            viewHolder.itemView.elevation = 0f
        }
    }

    companion object {
        const val SHADOW_ELEVATION = 16f
    }
}
