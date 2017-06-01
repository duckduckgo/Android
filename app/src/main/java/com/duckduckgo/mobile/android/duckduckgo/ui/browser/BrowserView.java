package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.List;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserView {
    void navigateToTabSwitcher(List<Tab> tabs);

    void createNewTabs();

    void switchToTab(int position);

    void removeTab(int position);

    void removeAllTabs();
}
