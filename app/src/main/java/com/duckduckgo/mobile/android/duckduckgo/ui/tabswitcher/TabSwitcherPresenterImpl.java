package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public class TabSwitcherPresenterImpl implements TabSwitcherPresenter {

    private TabSwitcherView tabSwitcherView;
    private List<Tab> tabsToDelete;

    public TabSwitcherPresenterImpl() {
        tabsToDelete = new ArrayList<>();
    }

    @Override
    public void attach(TabSwitcherView tabSwitcherView) {
        this.tabSwitcherView = tabSwitcherView;
    }

    @Override
    public void detachView() {
        tabSwitcherView = null;
    }

    @Override
    public void createNewTab() {
        tabSwitcherView.resultCreateNewTab();
    }

    @Override
    public void openTab(Tab tab) {
        tabSwitcherView.resultSelectTab(tab.index);
    }

    @Override
    public void closeTab(Tab tab) {
        tabsToDelete.add(tab);
        //tabSwitcherView.resultRemoveTab(tab.index);
    }

    @Override
    public void closeAllTabs() {
        tabSwitcherView.resultRemoveAllTabs();
    }

    @Override
    public void openSettings() {

    }

    @Override
    public void closeTabSwitcher() {
        tabSwitcherView.closeTabSwitcher();
    }
}
