package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.main.MainView;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.OmnibarView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabView;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserPresenter {
    void attachMainview(@NonNull MainView mainView);

    void detachMainView();

    void attachOmnibarView(@NonNull OmnibarView omnibarView);

    void detachOmnibarView();

    void attachBrowserView(@NonNull BrowserView browserView);

    void detachBrowserView();

    void attachTabView(@NonNull TabView tabView);

    void detachTabView();

    void loadTabs();

    void createNewTab();

    void openTab(@NonNull String tabId);

    void closeTab(@NonNull String tabId);

    void requestSearch(@Nullable String text);

    void requestAssist();

    void navigateHistoryForward();

    void navigateHistoryBackward();

    void refreshCurrentPage();

    void onReceiveTitle(@NonNull String tabId, @NonNull String title);

    void onPageStarted(@NonNull String tabId, @Nullable String url);

    void onPageFinished(@NonNull String tabId, @Nullable String url);

    void onProgressChanged(@NonNull String tabId, int newProgress);

    boolean handleBackHistory();

    void viewBookmarks();

    void requestSaveCurrentPageAsBookmark();

    void saveBookmark(@NonNull BookmarkEntity bookmarkEntity);

    void loadBookmark(@NonNull BookmarkEntity bookmarkEntity);
}
