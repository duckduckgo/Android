package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkModel;

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

    void showConfirmSaveBookmark(@NonNull BookmarkModel bookmarkModel);

    void navigateToBookmarks();
}
