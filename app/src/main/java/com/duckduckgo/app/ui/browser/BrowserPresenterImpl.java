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

import com.duckduckgo.app.domain.bookmark.BookmarkRepository;
import com.duckduckgo.app.domain.suggestion.SuggestionRepository;
import com.duckduckgo.app.domain.tab.Tab;
import com.duckduckgo.app.domain.tab.TabRepository;
import com.duckduckgo.app.ui.autocomplete.AutocompleteTask;
import com.duckduckgo.app.ui.autocomplete.AutocompleteView;
import com.duckduckgo.app.ui.autocomplete.SuggestionEntity;
import com.duckduckgo.app.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.app.ui.main.MainView;
import com.duckduckgo.app.ui.navigationbar.NavigationBarView;
import com.duckduckgo.app.ui.omnibar.OmnibarView;
import com.duckduckgo.app.ui.tab.TabEntity;
import com.duckduckgo.app.ui.tab.TabView;
import com.duckduckgo.app.ui.tabswitcher.TabSwitcherView;
import com.duckduckgo.app.util.AppUrls;
import com.duckduckgo.app.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;

public class BrowserPresenterImpl implements BrowserPresenter {

    private static final int PROGRESS_COMPLETE = 100;

    private static final int REQUEST_NO_ACTION = 0;
    private static final int REQUEST_ASSIST = 10;
    private static final int REQUEST_SEARCH_NEW_TAB = 11;

    private int requestAction = REQUEST_NO_ACTION;
    private String requestSearch = null;

    private MainView mainView;
    private OmnibarView omnibarView;
    private NavigationBarView navigationBarView;
    private BrowserView browserView;
    private TabView tabView;
    private AutocompleteView autocompleteView;
    private TabSwitcherView tabSwitcherView;

    private TabRepository tabRepository;
    private BookmarkRepository bookmarkRepository;
    private SuggestionRepository suggestionRepository;

    private List<TabEntity> tabs = new ArrayList<>();
    private int currentIndex = -1;

    private List<SuggestionEntity> suggestions = new ArrayList<>();

    private boolean isEditing = false;

    public BrowserPresenterImpl(@NonNull TabRepository tabRepository, @NonNull BookmarkRepository bookmarkRepository,
                                @NonNull SuggestionRepository suggestionRepository) {
        this.tabRepository = tabRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    public void attachMainview(@NonNull MainView mainView) {
        this.mainView = mainView;
    }

    @Override
    public void detachMainView() {
        mainView = null;
    }

    @Override
    public void attachOmnibarView(@NonNull OmnibarView omnibarView) {
        this.omnibarView = omnibarView;
    }

    @Override
    public void detachOmnibarView() {
        omnibarView = null;
    }

    @Override
    public void attachNavigationBarView(@NonNull NavigationBarView navigationBarView) {
        this.navigationBarView = navigationBarView;
    }

    @Override
    public void detachNavigationBarView() {
        navigationBarView = null;
    }

    @Override
    public void attachBrowserView(@NonNull BrowserView browserView) {
        this.browserView = browserView;
    }

    @Override
    public void detachBrowserView() {
        browserView = null;
    }

    @Override
    public void attachTabView(@NonNull TabView tabView) {
        this.tabView = tabView;
    }

    @Override
    public void detachTabView() {
        tabView = null;
    }

    @Override
    public void attachAutocompleteView(@NonNull AutocompleteView autocompleteView) {
        this.autocompleteView = autocompleteView;
    }

    @Override
    public void detachAutocompleteView() {
        autocompleteView = null;
    }

    @Override
    public void attachTabSwitcherView(@NonNull TabSwitcherView tabSwitcherView) {
        this.tabSwitcherView = tabSwitcherView;
    }

    @Override
    public void detachTabSwitcherView() {
        tabSwitcherView = null;
    }

    @Override
    public void loadTabs(boolean restoreSession) {
        if (restoreSession) {
            tabs.clear();
            for (Tab tab : tabRepository.getAll()) {
                tabs.add(new TabEntity(tab));
            }
            currentIndex = 0;
        }
        tabRepository.deleteAll();
        browserView.clearBrowser();

        switch (requestAction) {
            case REQUEST_ASSIST:
                actionAssist();
                break;
            case REQUEST_SEARCH_NEW_TAB:
                if (requestSearch != null) {
                    actionSearchInNewTab(requestSearch);
                }
                break;
            case REQUEST_NO_ACTION:
            default:
                if (tabs.size() == 0) {
                    TabEntity tab = createNewTab();

                    currentIndex = tabs.indexOf(tab);
                }
                showTab(currentIndex);
        }
        requestAction = REQUEST_NO_ACTION;
        requestSearch = null;


    }

    @Override
    public void saveSession() {
        tabRepository.deleteAll();
        for (TabEntity tab : tabs) {
            tabRepository.insert(tab);
        }
    }

    private void showTab(int index) {
        resetToolbars();

        currentIndex = index;
        TabEntity currentTab = getCurrentTab();

        browserView.showTab(currentTab.getId());
        setToolbarsForTab(currentTab);

    }

    @Override
    public void openNewTab() {
        TabEntity tab = createNewTab();
        int index = tabs.indexOf(tab);
        dismissTabSwitcher();
        showTab(index);
    }

    private TabEntity createNewTab() {
        TabEntity tab = TabEntity.create();
        tabs.add(tab);
        browserView.createNewTab(tab.getId());
        return tab;
    }

    @Override
    public void openTab(int index) {
        dismissTabSwitcher();
        showTab(index);
    }

    @Override
    public void closeTab(int index) {
        TabEntity tab = tabs.get(index);
        tabs.remove(tab);
        loadTabsSwitcherTabs();
        browserView.deleteTab(tab.getId());
        if (currentIndex < index) return;
        if (currentIndex > index) currentIndex--;
        if (tabs.size() == 0) {
            createNewTab();

            currentIndex = 0;
            showTab(currentIndex);
        } else if (currentIndex >= tabs.size()) {
            currentIndex = tabs.size() - 1;
        }
    }

    @Override
    public void fire() {
        browserView.deleteAllTabs();
        browserView.deleteAllPrivacyData();
        tabRepository.deleteAll();
        tabs.clear();

        loadTabsSwitcherTabs();

    }

    @Override
    public void loadTabsSwitcherTabs() {
        if (tabSwitcherView != null) {
            tabSwitcherView.showTabs(tabs);
            if (tabs.size() == 0) {
                tabSwitcherView.showNoTabsTitle();
            } else {
                tabSwitcherView.showTitle();
            }
        }
    }

    @Override
    public void openTabSwitcher() {
        mainView.navigateToTabSwitcher();
    }

    @Override
    public void dismissTabSwitcher() {
        if (tabSwitcherView != null) {
            mainView.dismissTabSwitcher();
            if (tabs.size() == 0) openNewTab();
        }
    }

    @Override
    public void requestSearchInCurrentTab(@Nullable String text) {
        requestSearch(text);
    }

    @Override
    public void requestSearchInNewTab(@Nullable String text) {
        if (browserView == null) {
            requestAction = REQUEST_SEARCH_NEW_TAB;
            requestSearch = text;
        } else {
            actionSearchInNewTab(text);
        }

    }

    private void actionSearchInNewTab(@Nullable String text) {
        dismissTabSwitcher();
        openNewTab();
        requestSearch(text);
    }

    private void requestSearch(@Nullable String text) {
        if (text == null) return;
        if (isEditing) setEditing(false);
        if (UrlUtils.isUrl(text)) {
            String url = UrlUtils.getUrlWithScheme(text);
            requestLoadUrl(url);
        } else {
            requestQuerySearch(text);
        }
    }

    private void requestLoadUrl(@NonNull String url) {
        tabView.loadUrl(url);
    }

    private void requestQuerySearch(@NonNull String query) {
        if (isEditing) {
            setEditing(false);
        }
        String url = AppUrls.getSearchUrl(query);
        requestLoadUrl(url);
    }

    @Override
    public void requestAssist() {
        if (browserView == null) {
            requestAction = REQUEST_ASSIST;
        } else {
            actionAssist();
        }
    }

    private void actionAssist() {
        dismissTabSwitcher();
        openNewTab();
        omnibarView.requestSearchFocus();
    }

    @Override
    public void omnibarFocusChanged(boolean focused) {
        if (focused) {
            setEditing(true);
            TabEntity currentTab = getCurrentTab();
            if (currentTab != null && currentTab.getCurrentUrl().length() > 0) {
                omnibarView.setDeleteAllTextButtonVisible(true);
            }
        }
    }

    private void setEditing(boolean editing) {
        isEditing = editing;
        setOmnibarEditing(isEditing);
        if (!isEditing) {
            hideAutocompleteResults();
        }
    }

    private void setOmnibarEditing(boolean isEditing) {
        this.isEditing = isEditing;
        omnibarView.setEditing(isEditing);
        if (!isEditing) {
            omnibarView.clearFocus();
            omnibarView.setDeleteAllTextButtonVisible(false);
        }
    }

    @Override
    public void omnibarTextChanged(@NonNull String text) {
        if (isEditing) {
            omnibarView.setDeleteAllTextButtonVisible(text.length() > 0);
            if (text.length() > 0) {
                loadAutocompleteResults(text);
            } else {
                hideAutocompleteResults();
            }
        }
    }

    @Override
    public void cancelOmnibarFocus() {
        setEditing(false);
        cancelOmnibarText();
        TabEntity currentTab = getCurrentTab();
        if (currentTab != null) {
            displayTextForUrl(currentTab.getCurrentUrl());
        }
        omnibarView.closeKeyboard();
    }

    @Override
    public void cancelOmnibarText() {
        omnibarView.clearText();
        hideAutocompleteResults();
    }

    @Override
    public void navigateHistoryForward() {
        tabView.goForward();
        setCanGoBack(true);
    }

    @Override
    public void navigateHistoryBackward() {
        tabView.goBack();
        setCanGoForward(true);
    }

    @Override
    public void refreshCurrentPage() {
        tabView.reload();
    }

    @Override
    public void onReceiveTitle(@NonNull String tabId, @NonNull String title) {
        TabEntity tab = getTabForId(tabId);
        if (tab != null) {
            tab.setTitle(title);
        }
    }

    @Override
    public void onReceivedIcon(@NonNull String tabId, @NonNull Bitmap favicon) {
        TabEntity tab = getTabForId(tabId);
        if (tab != null) {
            tab.setFavicon(favicon);
        }
    }

    @Override
    public void onPageStarted(@NonNull String tabId, @Nullable String url) {
        TabEntity tab = getTabForId(tabId);
        if (tab != null) {
            tab.setFavicon(null);
            tab.setCurrentUrl(url);
        }
        omnibarView.setRefreshEnabled(true);
        String validUrl = url == null ? "" : url;
        displayTextForUrl(validUrl);
    }

    @Override
    public void onPageFinished(@NonNull String tabId, @Nullable String url) {
        if (!isCurrentTab(tabId)) return;
        setNavigationMenuButtonsEnabled();
    }

    @Override
    public void onHistoryChanged(@NonNull String tabId, boolean canGoBack, boolean canGoForward) {
        setNavigationForTab(tabId, canGoBack, canGoForward);
    }

    @Override
    public void onProgressChanged(@NonNull String tabId, int newProgress) {
        if (!isCurrentTab(tabId)) return;
        if (omnibarView == null) return;
        if (newProgress < PROGRESS_COMPLETE) {
            omnibarView.showProgressBar();
        } else if (newProgress == PROGRESS_COMPLETE) {
            omnibarView.hideProgressBar();
        }

        omnibarView.onProgressChanged(newProgress);
    }

    @Override
    public boolean handleBackNavigation() {
        if (tabSwitcherView != null) {
            dismissTabSwitcher();
            return true;
        } else if (isEditing) {
            cancelOmnibarFocus();
            return true;
        } else if (tabView.canGoBack()) {
            navigateHistoryBackward();
            return true;
        }
        return false;
    }

    @Override
    public void viewBookmarks() {
        mainView.navigateToBookmarks();
    }

    @Override
    public void requestSaveCurrentPageAsBookmark() {
        BookmarkEntity bookmarkEntity = BookmarkEntity.create();
        bookmarkEntity.setIndex(bookmarkRepository.getAll().size());
        TabEntity currentTab = getCurrentTab();
        if (currentTab == null) return;
        bookmarkEntity.setName(currentTab.getTitle());
        bookmarkEntity.setUrl(currentTab.getCurrentUrl());
        mainView.showConfirmSaveBookmark(bookmarkEntity);
    }

    @Override
    public void saveBookmark(@NonNull BookmarkEntity bookmarkEntity) {
        bookmarkRepository.insert(bookmarkEntity);
    }

    @Override
    public void loadBookmark(@NonNull BookmarkEntity bookmarkEntity) {
        requestLoadUrl(bookmarkEntity.getUrl());
    }

    @Override
    public void requestCopyCurrentUrl() {
        TabEntity tab = getCurrentTab();
        if (tab == null) return;
        String url = tab.getCurrentUrl();
        mainView.copyUrlToClipboard(url);
    }

    @Override
    public void autocompleteSuggestionClicked(int position) {
        SuggestionEntity suggestion = getSuggestion(position);
        if (suggestion == null) return;
        requestSearchInCurrentTab(suggestion.getSuggestion());
        omnibarView.closeKeyboard();
    }

    @Override
    public void autocompleteSuggestionAddToQuery(int position) {
        SuggestionEntity suggestion = getSuggestion(position);
        if (suggestion == null) return;
        displayText(suggestion.getSuggestion());
    }

    @Override
    public void onReceiveNewSuggestionsForQuery(@NonNull List<SuggestionEntity> list, @NonNull String query) {
        suggestions.clear();
        suggestions.addAll(list);
        autocompleteView.addSuggestions(suggestions, query);
    }

    @Nullable
    private SuggestionEntity getSuggestion(int position) {
        if (position >= suggestions.size()) return null;
        return suggestions.get(position);
    }

    private void loadAutocompleteResults(@NonNull String query) {
        showAutcompleteResults();
        mainView.loadSuggestions(suggestionRepository, query);
    }

    private void showAutcompleteResults() {
        autocompleteView.show();
    }

    private void hideAutocompleteResults() {
        autocompleteView.hide();
    }

    private void setCanGoBack() {
        if (tabView == null) return;
        setCanGoBack(tabView.canGoBack());
    }

    private void setCanGoForward() {
        if (tabView == null) return;
        setCanGoForward(tabView.canGoForward());
    }

    private void setCanGoBack(boolean canGoBack) {
        navigationBarView.setBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        navigationBarView.setForwardEnabled(canGoForward);
    }

    private void setNavigationMenuButtonsEnabled() {
        setCanGoBack();
        setCanGoForward();
    }

    private void setNavigationForTab(@NonNull String tabId, boolean canGoBack, boolean canGoForward) {
        TabEntity tab = getTabForId(tabId);
        if (tab == null) return;
        tab.setCanGoBack(canGoBack);
        tab.setCanGoForward(canGoForward);

    }

    private void resetToolbars() {
        resetOmnibar();
        resetNavigationBar();
    }

    private void resetOmnibar() {
        omnibarView.clearText();
        omnibarView.clearFocus();
        omnibarView.hideProgressBar();
        omnibarView.setRefreshEnabled(false);
    }

    private void resetNavigationBar() {
        navigationBarView.setBackEnabled(false);
        navigationBarView.setForwardEnabled(false);
    }

    private void setToolbarsForTab(TabEntity tab) {
        setOmnibarForTab(tab);
        setNavigationBarForTab(tab);
    }

    private void setOmnibarForTab(TabEntity tab) {
        displayTextForUrl(tab.getCurrentUrl());
        omnibarView.setRefreshEnabled(true);
    }

    private void setNavigationBarForTab(TabEntity tab) {
        navigationBarView.setBackEnabled(tab.canGoBack());
        navigationBarView.setForwardEnabled(tab.canGoForward());
    }

    private void displayText(String textToDisplay) {
        omnibarView.displayText(textToDisplay);
    }

    private void displayTextForUrl(String url) {
        if (AppUrls.isDuckDuckGo(url)) {
            displayTextForDuckDuckGoUrl(url);
        } else {
            displayText(url);
        }
    }

    private void displayTextForDuckDuckGoUrl(String url) {
        String textToDisplay = url;
        String query = AppUrls.getQuery(url);
        if (query != null && query.length() > 0) {
            textToDisplay = query;
            displayText(textToDisplay);
        }
    }

    private boolean isCurrentTab(@NonNull String tabId) {
        return getCurrentTab() != null && getCurrentTab().getId().equals(tabId);
    }

    @Nullable
    private TabEntity getCurrentTab() {
        if (currentIndex < 0 || tabs.size() <= currentIndex) return null;
        return tabs.get(currentIndex);
    }

    @Nullable
    private TabEntity getTabForId(@NonNull String id) {
        for (TabEntity tab : tabs) {
            if (tab.getId().equals(id)) {
                return tab;
            }
        }
        return null;
    }
}
