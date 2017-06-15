package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

import java.util.List;

/**
 * Created by fgei on 6/14/17.
 */

public interface TabSwitcherView {
    void showTabs(List<TabEntity> tabs);
}
