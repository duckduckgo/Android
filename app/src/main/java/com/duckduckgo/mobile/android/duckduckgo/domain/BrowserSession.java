package com.duckduckgo.mobile.android.duckduckgo.domain;

/**
 * Created by fgei on 6/12/17.
 */

public interface BrowserSession {
    String getTitle();

    String getCurrentUrl();

    boolean canGoBack();

    boolean canGoForward();
}
