package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.annotation.NonNull;
import android.util.Log;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public class TabSwitcherPresenterImpl implements TabSwitcherPresenter {

    private TabSwitcherView tabSwitcherView;
    private List<Tab> tabs;
    private List<Tab> tabsToDelete;

    public TabSwitcherPresenterImpl() {
        tabs = new ArrayList<>();
        tabsToDelete = new ArrayList<>();
    }

    @Override
    public void attach(@NonNull TabSwitcherView tabSwitcherView) {
        this.tabSwitcherView = tabSwitcherView;
        tabSwitcherView.loadTabs(tabs);
    }

    @Override
    public void detachView() {
        tabSwitcherView = null;
        tabsToDelete.clear();
    }

    @Override
    public void load(List<Tab> tabs) {
        this.tabs = tabs;
    }

    @Override
    public void restoreState(List<Tab> tabs, List<Tab> tabsToRemove) {
        this.tabs = tabs;
        this.tabsToDelete = tabsToRemove;
    }

    @Override
    public List<Tab> saveStateTabs() {
        return tabs;
    }

    @Override
    public List<Tab> saveStateTabsToRemove() {
        return tabsToDelete;
    }

    @Override
    public void createNewTab() {
        tabSwitcherView.resultCreateNewTab(getPositionsToDelete());
    }

    @Override
    public void openTab(Tab tab) {
        tabSwitcherView.resultSelectTab(tab.index, getPositionsToDelete());
    }

    @Override
    public void closeTab(Tab tab) {
        Log.e("tab_manager", "tabswitcher presenter, closeTab: " + tab.toString());
        tabsToDelete.add(tab);
        //tabSwitcherView.resultRemoveTab(tab.index);
    }

    @Override
    public void closeAllTabs() {
        tabSwitcherView.resultRemoveAllTabs();
    }

    @Override
    public void closeTabSwitcher() {
        tabSwitcherView.closeTabSwitcher(getPositionsToDelete());
    }

    private List<Integer> getPositionsToDelete() {
        List<Integer> out = new ArrayList<>();
        for (Tab tab : tabsToDelete) {
            out.add(tab.index);
        }
        return out;
    }/*

    private List<String> getTabIdsToDelete() {
        List<String> ids = new ArrayList<>();
        for(Tab tab : tabsToDelete) {
            ids.add(tab.id);
        }
        return ids;
    }*/
}
