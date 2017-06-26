package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.util.DiffUtil;

import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

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

public class TabDiffCallback extends DiffUtil.Callback {

    private List<TabEntity> oldList;
    private List<TabEntity> newList;

    public TabDiffCallback(List<TabEntity> oldList, List<TabEntity> newList) {
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
        TabEntity oldItem = oldList.get(oldItemPosition);
        TabEntity newItem = newList.get(newItemPosition);
        return oldItem.getId().equals(newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        TabEntity oldItem = oldList.get(oldItemPosition);
        TabEntity newItem = newList.get(newItemPosition);
        return oldItem.getTitle().equals(newItem.getTitle())
                && oldItem.getCurrentUrl().equals(newItem.getCurrentUrl());
    }
}
