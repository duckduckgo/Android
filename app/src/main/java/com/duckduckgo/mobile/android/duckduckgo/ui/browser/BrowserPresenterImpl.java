package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;
import com.duckduckgo.mobile.android.duckduckgo.util.UrlUtils;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterImpl implements BrowserPresenter {

    private static final int PROGRESS_COMPLETE = 100;

    private BrowserView browserView;
    private OmnibarView omnibarView;

    private TabView tabView;

    public BrowserPresenterImpl() {
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
    public void detachViews() {
        browserView = null;
        omnibarView = null;
        tabView = null;
    }

    @Override
    public void openTabSwitcher() {
        browserView.navigateToTabSwitcher();
    }

    @Override
    public void createNewTab() {
    }

    @Override
    public void openTab(int position) {
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
        omnibarView.setRefreshEnabled(true);
        String validUrl = url == null ? "" : url;
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
        if (tabView.canGoBack()) {
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
        omnibarView.setBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        omnibarView.setForwardEnabled(canGoForward);
    }

    private void setNavigationMenuButtonsEnabled() {
        setCanGoBack();
        setCanGoForward();
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
}
