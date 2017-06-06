package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabManager;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabView;
import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;
import com.duckduckgo.mobile.android.duckduckgo.util.UrlUtils;

import java.util.List;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterImpl implements BrowserPresenter {

    private static final int PROGRESS_COMPLETE = 100;

    private BrowserView browserView;
    private OmnibarView omnibarView;
    private TabView tabView;

    private TabManager tabManager;

    public BrowserPresenterImpl(TabManager tabManager) {
        this.tabManager = tabManager;
        this.tabManager.setOnTabListener(new TabManager.OnTabListener() {
            @Override
            public void onTabCreated(Tab tabCreated) {
                browserView.createNewTab(tabCreated.id);
            }

            @Override
            public void onTabRemoved(Tab tabRemoved) {
                browserView.removeTab(tabRemoved.id);
            }

            @Override
            public void onCurrentTabChanged(Tab currentTab) {
                showTab(currentTab);
            }
        });
    }

    @Override
    public void attachBrowserView(@NonNull BrowserView browserView) {
        this.browserView = browserView;
    }

    @Override
    public void attachOmnibarView(@NonNull OmnibarView omnibarView) {
        this.omnibarView = omnibarView;
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
    public void detachViews() {
        browserView = null;
        omnibarView = null;
        tabView = null;
    }

    @Override
    public void load() {
        if (tabManager.getCurrentTab() == null) {
            createNewTab();
        } else {
            showTab(tabManager.getCurrentTab());
        }
    }

    @Override
    public void restore(@NonNull List<Tab> tabs, int currentIndex) {
        tabManager.setTabs(tabs);
        tabManager.selectTab(currentIndex);
    }

    @Override
    public List<Tab> saveTabs() {
        return tabManager.getTabs();
    }

    @Override
    public int saveCurrentIndex() {
        return tabManager.getCurrentTab().index;
    }

    @Override
    public void openTabSwitcher() {
        browserView.navigateToTabSwitcher(tabManager.getTabs());
    }

    @Override
    public void createNewTab() {
        tabManager.createNewTab();
    }

    @Override
    public void openTab(int position) {
        tabManager.selectTab(position);
    }

    @Override
    public void removeTabs(@NonNull List<Integer> positionsToRemove) {
        tabManager.removeTabs(positionsToRemove);
    }

    @Override
    public void removeAllTabs() {
        tabManager.removeAll();
        createNewTab();
    }

    private void showTab(Tab tab) {
        omnibarView.clearText();
        omnibarView.clearFocus();
        String displayText = tab.name != null ? tab.name : tab.currentUrl;
        displayText(displayText);
        omnibarView.setBackEnabled(tab.canGoBack);
        omnibarView.setForwardEnabled(tab.canGoForward);
        browserView.switchToTab(tab.id);
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

    @Override
    public void requestSearchInNewTab(@Nullable String text) {
        tabManager.createNewTab();
        requestSearch(text);
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
        createNewTab();
        omnibarView.requestFocus();
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
    public void onPageStarted(@Nullable String url) {
        String validUrl = url == null ? "" : url;
        tabManager.getCurrentTab().currentUrl = validUrl;
        omnibarView.setRefreshEnabled(true);
        displayTextForUrl(validUrl);
    }

    @Override
    public void onPageFinished(@Nullable String url) {
        setNavigationMenuButtonsEnabled();
    }

    @Override
    public void onProgressChanged(int newProgress) {
        if (omnibarView == null) return;
        if (newProgress < PROGRESS_COMPLETE) {
            omnibarView.showProgressBar();
        } else if (newProgress == PROGRESS_COMPLETE) {
            omnibarView.hideProgressBar();
        }
        omnibarView.onProgressChanged(newProgress);
    }

    @Override
    public boolean handleBackHistory() {
        if (tabView != null && tabView.canGoBack()) {
            navigateHistoryBackward();
            return true;
        }
        return false;
    }

    private void setCanGoBack() {
        setCanGoBack(tabView.canGoBack());
    }

    private void setCanGoForward() {
        setCanGoForward(tabView.canGoForward());
    }

    private void setCanGoBack(boolean canGoBack) {
        tabManager.getCurrentTab().canGoBack = canGoBack;
        omnibarView.setBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        tabManager.getCurrentTab().canGoForward = canGoForward;
        omnibarView.setForwardEnabled(canGoForward);
    }

    private void setNavigationMenuButtonsEnabled() {
        setCanGoBack();
        setCanGoForward();
    }

    private void displayText(String textToDisplay) {
        tabManager.getCurrentTab().name = textToDisplay;
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
}
