package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.util.DiffUtil;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.List;

/**
 * Created by fgei on 5/31/17.
 */

public class TabsDiffCallback extends DiffUtil.Callback {

    private List<Tab> oldTabs;
    private List<Tab> newTabs;

    public TabsDiffCallback(List<Tab> oldTabs, List<Tab> newTabs) {
        this.oldTabs = oldTabs;
        this.newTabs = newTabs;
    }

    @Override
    public int getOldListSize() {
        return oldTabs.size();
    }

    @Override
    public int getNewListSize() {
        return newTabs.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        Tab oldTab = oldTabs.get(oldItemPosition);
        Tab newTab = newTabs.get(newItemPosition);
        return oldTab.index == newTab.index;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return true;
    }
}
