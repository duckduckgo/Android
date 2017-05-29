package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabSwitcherView {
    void closeTabSwitcher();

    void createNewTab();

    void selectTab(int position);
    //void closeTabSwitcherWithTabSelected();
}
