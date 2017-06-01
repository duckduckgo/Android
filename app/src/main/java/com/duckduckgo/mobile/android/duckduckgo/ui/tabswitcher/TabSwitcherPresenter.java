package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabSwitcherPresenter {
    void attach(TabSwitcherView tabSwitcherView);

    void detachView();

    void createNewTab();

    void openTab(Tab tab);

    void closeTab(Tab tab);

    void closeAllTabs();

    void openSettings();

    void closeTabSwitcher();
}
