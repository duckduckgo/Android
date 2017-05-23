package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserView {
    void loadUrl(@NonNull String url);
    void goBack();
    void goForward();
    boolean canGoBack();
    boolean canGoForward();
    void reload();
}
