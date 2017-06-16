package com.duckduckgo.mobile.android.duckduckgo.domain.tab;

/**
 * Created by fgei on 6/14/17.
 */

public interface Tab {
    String getId();

    String getTitle();

    String getCurrentUrl();

    boolean canGoBack();

    boolean canGoForward();
}
