package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.itemtouchhelper.ItemTouchHelperAdapter;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.itemtouchhelper.OnStartDragListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarkViewHolder> implements ItemTouchHelperAdapter {

    interface OnBookmarkListener {
        void onBookmarkSelected(View v, int position);

        void onBookmarkDeleted(View v, int position);

        void onBookmarksSwap(int fromPosition, int toPosition);
    }

    private List<BookmarkModel> bookmarks = new ArrayList<>();
    private boolean editable = false;

    private OnStartDragListener onStartDragListener;
    private OnBookmarkListener onBookmarkListener;

    public BookmarksAdapter(OnStartDragListener onStartDragListener, OnBookmarkListener onBookmarkListener) {
        this.onStartDragListener = onStartDragListener;
        this.onBookmarkListener = onBookmarkListener;
    }

    public void setBookmarks(List<BookmarkModel> bookmarks) {
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
        BookmarkModel bookmarkModel = bookmarks.get(position);
        holder.setBookmark(bookmarkModel);
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

    private void updateList(List<BookmarkModel> newList) {
        BookmarkDiffCallback diffCallback = new BookmarkDiffCallback(bookmarks, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        bookmarks.clear();
        bookmarks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}
