package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabSwitcherPresenter {
    void attach(@NonNull TabSwitcherView tabSwitcherView);

    void detachView();

    void load(List<Tab> tabs);

    void restoreState(List<Tab> tabs, List<Tab> tabsToRemove);

    List<Tab> saveStateTabs();

    List<Tab> saveStateTabsToRemove();

    void createNewTab();

    void openTab(Tab tab);

    void closeTab(Tab tab);

    void closeAllTabs();

    void closeTabSwitcher();
}
