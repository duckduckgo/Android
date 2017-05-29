package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabSwitcherPresenter {
    void attach(TabSwitcherView tabSwitcherView);

    void detachView();

    void createNewTab();

    void openTab(int position);

    void openSettings();

    void closeTabSwitcher();

    void actionFire();
}
