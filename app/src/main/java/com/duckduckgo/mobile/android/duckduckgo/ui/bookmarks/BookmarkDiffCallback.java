package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.v7.util.DiffUtil;

import java.util.List;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
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
