package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabView;

import java.util.List;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserPresenter {
    void attachBrowserView(@NonNull BrowserView browserView);

    void attachOmnibarView(@NonNull OmnibarView omnibarView);

    void attachTabView(@NonNull TabView tabView);

    void detachTabView();

    void detachViews();

    void load();

    void restore(@NonNull List<Tab> tabs, int currentIndex);

    List<Tab> saveTabs();

    int saveCurrentIndex();

    void requestSearch(@Nullable String text);

    void requestSearchInNewTab(@Nullable String text);

    void requestAssist();

    void navigateHistoryForward();

    void navigateHistoryBackward();

    void refreshCurrentPage();

    void openTabSwitcher();

    void createNewTab();

    void openTab(int position);

    void removeTabs(@NonNull List<Integer> tabsToRemove);

    void removeAllTabs();

    void onPageStarted(@Nullable String url);

    void onPageFinished(@Nullable String url);

    void onProgressChanged(int newProgress);

    boolean handleBackHistory();
}
