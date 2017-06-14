package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.v7.util.DiffUtil;

import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkDiffCallback extends DiffUtil.Callback {

    private List<BookmarkEntity> oldList;
    private List<BookmarkEntity> newList;

    public BookmarkDiffCallback(List<BookmarkEntity> oldList, List<BookmarkEntity> newList) {
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
        BookmarkEntity oldItem = oldList.get(oldItemPosition);
        BookmarkEntity newItem = newList.get(newItemPosition);
        return oldItem.getId().equals(newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        BookmarkEntity oldItem = oldList.get(oldItemPosition);
        BookmarkEntity newItem = newList.get(newItemPosition);
        return oldItem.getName().equals(newItem.getName());
    }
}
