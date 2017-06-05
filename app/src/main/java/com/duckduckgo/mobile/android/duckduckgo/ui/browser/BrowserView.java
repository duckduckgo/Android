package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.List;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserView {
    void navigateToTabSwitcher(@NonNull List<Tab> tabs);

    void createNewTab(@NonNull String id);

    void switchToTab(@NonNull String id);

    void removeTab(@NonNull String id);

    void removeAllTabs();
}
