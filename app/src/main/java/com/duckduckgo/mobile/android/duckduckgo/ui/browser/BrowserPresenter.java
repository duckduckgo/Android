package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.main.MainView;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.OmnibarView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherView;

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

    void attachTabSwitcherView(@NonNull TabSwitcherView tabSwitcherView);

    void detachTabSwitcherView();

    void loadTabs(boolean restoreSession);

    void saveSession();

    void openNewTab();

    void openTab(int index);

    void closeTab(int index);

    void fire();

    void openTabSwitcher();

    void loadTabsSwitcherTabs();

    void dismissTabSwitcher();

    void requestSearchInCurrentTab(@Nullable String text);

    void requestSearchInNewTab(@Nullable String text);

    void requestAssist();

    void omnibarFocusChanged(boolean focused);

    void omnibarTextChanged(@NonNull String text);

    void cancelOmnibarText();

    void cancelOmnibarFocus();

    void navigateHistoryForward();

    void navigateHistoryBackward();

    void refreshCurrentPage();

    void onReceiveTitle(@NonNull String tabId, @NonNull String title);

    void onReceivedIcon(@NonNull String tabId, @NonNull Bitmap favicon);

    void onPageStarted(@NonNull String tabId, @Nullable String url);

    void onPageFinished(@NonNull String tabId, @Nullable String url);

    void onHistoryChanged(@NonNull String tabId, boolean canGoBack, boolean canGoForward);

    void onProgressChanged(@NonNull String tabId, int newProgress);

    boolean handleBackNavigation();

    void viewBookmarks();

    void requestSaveCurrentPageAsBookmark();

    void saveBookmark(@NonNull BookmarkEntity bookmarkEntity);

    void loadBookmark(@NonNull BookmarkEntity bookmarkEntity);
}
