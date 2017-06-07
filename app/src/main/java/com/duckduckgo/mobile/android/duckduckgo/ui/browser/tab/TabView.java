package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import android.support.annotation.NonNull;

/**
 * Created by fgei on 5/29/17.
 */

public interface TabView {
    void loadUrl(@NonNull String url);

    String getTitle();

    void goBack();

    void goForward();

    boolean canGoBack();

    boolean canGoForward();

    void reload();
}
