package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabSwitcherView {
    void loadTabs(@NonNull List<Tab> tabs);

    void closeTabSwitcher(@NonNull List<Integer> positionToDelete);

    void resultCreateNewTab(@NonNull List<Integer> positionToDelete);

    void resultSelectTab(int selectedIndex, @NonNull List<Integer> positionToDelete);

    void resultRemoveAllTabs();


}
