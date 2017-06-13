package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.v7.util.DiffUtil;

import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkDiffCallback extends DiffUtil.Callback {

    private List<BookmarkModel> oldList;
    private List<BookmarkModel> newList;

    public BookmarkDiffCallback(List<BookmarkModel> oldList, List<BookmarkModel> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        BookmarkModel oldItem = oldList.get(oldItemPosition);
        BookmarkModel newItem = newList.get(newItemPosition);
        return oldItem.getId().equals(newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        BookmarkModel oldItem = oldList.get(oldItemPosition);
        BookmarkModel newItem = newList.get(newItemPosition);
        return oldItem.getName().equals(newItem.getName());
    }
}
