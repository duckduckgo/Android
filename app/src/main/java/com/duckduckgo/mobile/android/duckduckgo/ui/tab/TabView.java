package com.duckduckgo.mobile.android.duckduckgo.ui.tab;

import android.support.annotation.NonNull;

/**
 * Created by fgei on 6/14/17.
 */

public interface TabView {
    void loadUrl(@NonNull String url);

    void goBack();

    void goForward();

    boolean canGoBack();

    boolean canGoForward();

    void reload();

    @NonNull
    String getTabId();

    void setTabId(@NonNull String tabId);
}
