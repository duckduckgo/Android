/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.ui.browser;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.app.ui.autocomplete.AutocompleteView;
import com.duckduckgo.app.ui.autocomplete.SuggestionEntity;
import com.duckduckgo.app.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.app.ui.main.MainView;
import com.duckduckgo.app.ui.navigationbar.NavigationBarView;
import com.duckduckgo.app.ui.omnibar.OmnibarView;
import com.duckduckgo.app.ui.tab.TabView;
import com.duckduckgo.app.ui.tabswitcher.TabSwitcherView;

import java.util.List;

public interface BrowserPresenter {
    void attachMainview(@NonNull MainView mainView);

    void detachMainView();

    void attachOmnibarView(@NonNull OmnibarView omnibarView);

    void detachOmnibarView();

    void attachNavigationBarView(@NonNull NavigationBarView navigationBarView);

    void detachNavigationBarView();

    void attachBrowserView(@NonNull BrowserView browserView);

    void detachBrowserView();

    void attachTabView(@NonNull TabView tabView);

    void detachTabView();

    void attachAutocompleteView(@NonNull AutocompleteView autocompleteView);

    void detachAutocompleteView();

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

    void requestCopyCurrentUrl();

    void autocompleteSuggestionClicked(int position);

    void autocompleteSuggestionAddToQuery(int position);

    void onReceiveNewSuggestionsForQuery(@NonNull List<SuggestionEntity> list, @NonNull String query);
}
