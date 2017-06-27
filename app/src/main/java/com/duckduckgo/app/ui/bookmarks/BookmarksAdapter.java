/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.ui.bookmarks;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.app.ui.base.itemtouchhelper.OnStartDragListener;
import com.duckduckgo.app.ui.bookmarks.itemtouchhelper.BookmarksTouchHelperAdapter;

import java.util.ArrayList;
import java.util.List;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarkViewHolder> implements BookmarksTouchHelperAdapter {

    interface OnBookmarkListener {
        void onBookmarkSelected(View v, int position);

        void onBookmarkDeleted(View v, int position);

        void onBookmarksSwap(int fromPosition, int toPosition);
    }

    private List<BookmarkEntity> bookmarks = new ArrayList<>();
    private boolean editable = false;

    private OnStartDragListener onStartDragListener;
    private OnBookmarkListener onBookmarkListener;

    public BookmarksAdapter(OnStartDragListener onStartDragListener, OnBookmarkListener onBookmarkListener) {
        this.onStartDragListener = onStartDragListener;
        this.onBookmarkListener = onBookmarkListener;
    }

    public void setBookmarks(List<BookmarkEntity> bookmarks) {
        updateList(bookmarks);
    }

    public void setEditable(boolean editable) {
        if (this.editable == editable) return;
        this.editable = editable;
        notifyItemRangeChanged(0, bookmarks.size());
    }

    @Override
    public BookmarkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return BookmarkViewHolder.inflate(parent, new BookmarkViewHolder.OnBookmarkListener() {
            @Override
            public void onClick(View view, int position) {
                onBookmarkListener.onBookmarkSelected(view, position);
            }

            @Override
            public void onDelete(View view, int position) {
                removeItem(view, position);
            }
        }, onStartDragListener);
    }

    @Override
    public void onBindViewHolder(BookmarkViewHolder holder, int position) {
        BookmarkEntity bookmarkEntity = bookmarks.get(position);
        holder.setBookmark(bookmarkEntity);
        holder.setEditable(editable);
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    @Override
    public boolean isEditable() {
        return editable;
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        onBookmarkListener.onBookmarksSwap(fromPosition, toPosition);
        return true;
    }

    @Override
    public void onItemDismiss(RecyclerView.ViewHolder holder, int position) {
        removeItem(holder.itemView, position);
    }

    private void removeItem(View view, int position) {
        onBookmarkListener.onBookmarkDeleted(view, position);
    }

    private void updateList(List<BookmarkEntity> newList) {
        BookmarkDiffCallback diffCallback = new BookmarkDiffCallback(bookmarks, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        bookmarks.clear();
        bookmarks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}
