package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.OmnibarView;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserPresenter {
    void attachBrowserView(@NonNull BrowserView browserView);

    void attachOmnibarView(@NonNull OmnibarView omnibarView);

    void detachViews();

    void requestSearch(@Nullable String text);

    void requestAssist();

    void navigateHistoryForward();

    void navigateHistoryBackward();

    void refreshCurrentPage();

    void onReceiveTitle(@NonNull String title);

    void onPageStarted(@Nullable String url);

    void onPageFinished(@Nullable String url);

    void onProgressChanged(int newProgress);

    boolean handleBackHistory();

    void viewBookmarks();

    void requestSaveCurrentPageAsBookmark();

    void saveBookmark(@NonNull BookmarkEntity bookmarkEntity);

    void loadBookmark(@NonNull BookmarkEntity bookmarkEntity);
}
