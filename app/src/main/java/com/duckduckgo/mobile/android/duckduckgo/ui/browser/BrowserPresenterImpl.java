package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.main.MainView;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.OmnibarView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabView;
import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;
import com.duckduckgo.mobile.android.duckduckgo.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterImpl implements BrowserPresenter {

    private static final int PROGRESS_COMPLETE = 100;

    private MainView mainView;
    private OmnibarView omnibarView;
    private BrowserView browserView;
    private TabView tabView;

    private BrowserSessionModel browserSessionModel;
    private BookmarkRepository bookmarkRepository;

    private List<TabEntity> tabs = new ArrayList<>();
    private int currentIndex = -1;

    public BrowserPresenterImpl(BookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
        browserSessionModel = new BrowserSessionModel();
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
    public void loadTabs() {
        TabEntity tab = TabEntity.create();
        tabs.add(tab);
        browserView.createNewTab(tab.getId());
        currentIndex = tabs.indexOf(tab);

        TabEntity currentTab = getCurrentTab();
        //if(currentTab == null) return;
        showTab(currentIndex);

    }

    private void showTab(int index) {
        resetOmnibar();

        currentIndex = index;
        TabEntity currentTab = getCurrentTab();
        if(currentTab == null) return;

        browserView.showTab(currentTab.getId());
        setOmnibarForTab(currentTab);

    }

    @Override
    public void createNewTab() {
        TabEntity tab = TabEntity.create();
        tabs.add(tab);
        browserView.createNewTab(tab.getId());
        int index = tabs.indexOf(tab);
        showTab(index);
    }

    @Override
    public void openTab(@NonNull String tabId) {

    }

    @Override
    public void closeTab(@NonNull String tabId) {

    }

    @Override
    public void requestSearch(@Nullable String text) {
        if (text == null) return;
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
        String url = AppUrls.getSearchUrl(query);
        requestLoadUrl(url);
    }

    @Override
    public void requestAssist() {
        omnibarView.clearText();
        omnibarView.requestSearchFocus();
    }

    @Override
    public void navigateHistoryForward() {
        tabView.goForward();
    }

    @Override
    public void navigateHistoryBackward() {
        tabView.goBack();
    }

    @Override
    public void refreshCurrentPage() {
        tabView.reload();
    }

    @Override
    public void onReceiveTitle(@NonNull String tabId, @NonNull String title) {
        TabEntity tab = getTabForId(tabId);
        if(tab != null) {
            tab.setTitle(title);
        }
        browserSessionModel.setTitle(title);
    }

    @Override
    public void onPageStarted(@NonNull String tabId, @Nullable String url) {
        TabEntity tab = getTabForId(tabId);
        if(tab != null) {
            tab.setCurrentUrl(url);
        }
        browserSessionModel.setCurrentUrl(url);
        omnibarView.setRefreshEnabled(true);
        String validUrl = url == null ? "" : url;
        displayTextForUrl(validUrl);
    }

    @Override
    public void onPageFinished(@NonNull String tabId, @Nullable String url) {
        if(!isCurrentTab(tabId)) return;
        setNavigationMenuButtonsEnabled();
    }

    @Override
    public void onProgressChanged(@NonNull String tabId, int newProgress) {
        if(!isCurrentTab(tabId)) return;
        if (omnibarView == null) return;
        browserSessionModel.setHasLoaded(newProgress == PROGRESS_COMPLETE);
        browserSessionModel.setProgress(newProgress);
        if (newProgress < PROGRESS_COMPLETE) {
            omnibarView.showProgressBar();
        } else if (newProgress == PROGRESS_COMPLETE) {
            omnibarView.hideProgressBar();
        }
        omnibarView.onProgressChanged(newProgress);
    }

    @Override
    public boolean handleBackHistory() {
        if (tabView.canGoBack()) {
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
        bookmarkEntity.setUrl(browserSessionModel.getCurrentUrl());
        bookmarkEntity.setName(browserSessionModel.getTitle());
        bookmarkEntity.setIndex(bookmarkRepository.getAll().size());
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

    private void setCanGoBack() {
        setCanGoBack(tabView.canGoBack());
    }

    private void setCanGoForward() {
        setCanGoForward(tabView.canGoForward());
    }

    private void setCanGoBack(boolean canGoBack) {
        browserSessionModel.setCanGoBack(canGoBack);
        omnibarView.setBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        browserSessionModel.setCanGoForward(canGoForward);
        omnibarView.setForwardEnabled(canGoForward);
    }

    private void setNavigationMenuButtonsEnabled() {
        setCanGoBack();
        setCanGoForward();
    }

    private void resetOmnibar() {
        omnibarView.clearText();
        omnibarView.clearFocus();
        omnibarView.hideProgressBar();
        omnibarView.setBackEnabled(false);
        omnibarView.setForwardEnabled(false);
        omnibarView.setRefreshEnabled(false);
    }

    private void setOmnibarForTab(TabEntity tab) {
        displayTextForUrl(tab.getCurrentUrl());
        omnibarView.setBackEnabled(tab.canGoBack());
        omnibarView.setForwardEnabled(tab.canGoForward());
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
        if(currentIndex < 0 || tabs.size() <= currentIndex) return null;
        return tabs.get(currentIndex);
    }

    @Nullable
    private TabEntity getTabForId(@NonNull String id) {
        for(TabEntity tab : tabs) {
            if(tab.getId().equals(id)) {
                return tab;
            }
        }
        return null;
    }
}
