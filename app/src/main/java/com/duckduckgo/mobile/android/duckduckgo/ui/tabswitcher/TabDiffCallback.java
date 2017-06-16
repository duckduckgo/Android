package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.util.DiffUtil;

import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

import java.util.List;

/**
 * Created by fgei on 6/14/17.
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
